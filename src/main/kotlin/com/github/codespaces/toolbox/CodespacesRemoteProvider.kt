package com.github.codespaces.toolbox

import com.github.codespaces.toolbox.cli.AuthStatus
import com.github.codespaces.toolbox.cli.GhCli
import com.github.codespaces.toolbox.models.Codespace
import com.github.codespaces.toolbox.views.SetupWizardPage
import com.jetbrains.toolbox.api.remoteDev.RemoteProvider
import com.jetbrains.toolbox.api.remoteDev.RemoteProviderEnvironment
import com.jetbrains.toolbox.api.ui.components.UiPage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Remote provider implementation for GitHub Codespaces.
 * Discovers and manages codespaces through the gh CLI.
 */
class CodespacesRemoteProvider(
    private val context: CodespacesContext
) : RemoteProvider("GitHub Codespaces") {

    private val ghCli = GhCli()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _environments = MutableStateFlow<List<RemoteProviderEnvironment>>(emptyList())
    override val environments: StateFlow<List<RemoteProviderEnvironment>> = _environments.asStateFlow()

    private val environmentCache = ConcurrentHashMap<String, CodespacesRemoteEnvironment>()

    private var pollingJob: Job? = null
    private var isAuthenticated = false

    /**
     * Start polling for codespaces.
     */
    fun poll() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive) {
                try {
                    refreshCodespaces()
                } catch (e: Exception) {
                    context.logger.error(e) { "Failed to refresh codespaces" }
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun refreshCodespaces() {
        val result = withContext(Dispatchers.IO) {
            ghCli.listCodespaces()
        }

        result.onSuccess { codespaces ->
            val environments = codespaces.map { codespace ->
                environmentCache.getOrPut(codespace.name) {
                    CodespacesRemoteEnvironment(codespace, ghCli, context)
                }.also { env ->
                    env.update(codespace)
                }
            }

            // Remove environments that no longer exist
            val currentNames = codespaces.map { it.name }.toSet()
            environmentCache.keys.removeIf { it !in currentNames }

            _environments.value = environments
        }.onFailure { error ->
            context.logger.error(error) { "Failed to list codespaces" }
        }
    }

    /**
     * Returns the setup wizard if not authenticated, null otherwise.
     */
    override fun getOverrideUiPage(): UiPage? {
        return when (ghCli.checkAuth()) {
            is AuthStatus.Authenticated -> {
                if (!isAuthenticated) {
                    isAuthenticated = true
                    poll()
                }
                null
            }
            is AuthStatus.NotAuthenticated -> {
                SetupWizardPage(
                    onAuthenticated = {
                        isAuthenticated = true
                        poll()
                    },
                    context = context
                )
            }
            is AuthStatus.NotInstalled -> {
                SetupWizardPage(
                    onAuthenticated = {
                        isAuthenticated = true
                        poll()
                    },
                    context = context,
                    ghNotInstalled = true
                )
            }
        }
    }

    /**
     * Handle incoming URI links (e.g., from browser).
     * Format: jetbrains://gateway/connect#codespace=<name>
     */
    override suspend fun handleUri(uri: String): Boolean {
        // Parse URI parameters
        val params = parseUriParams(uri)
        val codespaceName = params["codespace"] ?: return false

        context.logger.info { "Handling URI for codespace: $codespaceName" }

        // Find or create the environment
        val environment = environmentCache[codespaceName]
            ?: run {
                // Refresh to get the codespace
                refreshCodespaces()
                environmentCache[codespaceName]
            }

        if (environment != null) {
            // Trigger connection
            environment.connect()
            return true
        }

        context.logger.warn { "Could not find codespace: $codespaceName" }
        return false
    }

    private fun parseUriParams(uri: String): Map<String, String> {
        val fragment = uri.substringAfter('#', "")
        return fragment.split('&')
            .mapNotNull { param ->
                val parts = param.split('=', limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else null
            }
            .toMap()
    }

    /**
     * Clean up resources.
     */
    fun dispose() {
        pollingJob?.cancel()
        scope.cancel()
    }

    companion object {
        private const val POLL_INTERVAL_MS = 5000L
    }
}
