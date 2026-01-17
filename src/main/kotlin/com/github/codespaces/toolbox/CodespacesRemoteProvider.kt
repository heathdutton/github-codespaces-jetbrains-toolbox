package com.github.codespaces.toolbox

import com.github.codespaces.toolbox.cli.AuthStatus
import com.github.codespaces.toolbox.cli.GhCli
import com.jetbrains.toolbox.api.core.ui.icons.SvgIcon
import com.jetbrains.toolbox.api.core.util.LoadableState
import com.jetbrains.toolbox.api.localization.LocalizableString
import com.jetbrains.toolbox.api.remoteDev.ProviderVisibilityState
import com.jetbrains.toolbox.api.remoteDev.RemoteProvider
import com.jetbrains.toolbox.api.remoteDev.RemoteProviderEnvironment
import com.jetbrains.toolbox.api.ui.actions.ActionDescription
import com.jetbrains.toolbox.api.ui.components.UiPage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/**
 * Remote provider implementation for GitHub Codespaces.
 * Discovers and manages codespaces through the gh CLI.
 */
class CodespacesRemoteProvider(
    private val context: CodespacesContext
) : RemoteProvider("GitHub Codespaces") {

    private val ghCli = GhCli()
    private val environmentCache = ConcurrentHashMap<String, CodespacesRemoteEnvironment>()

    private var pollingJob: Job? = null
    private var isAuthenticated = false

    private val _environments = MutableStateFlow<LoadableState<List<CodespacesRemoteEnvironment>>>(
        LoadableState.Loading
    )
    override val environments: MutableStateFlow<LoadableState<List<CodespacesRemoteEnvironment>>> = _environments

    override val svgIcon: SvgIcon = SvgIcon(GITHUB_ICON_SVG.toByteArray())

    override val noEnvironmentsSvgIcon: SvgIcon? = null

    override val noEnvironmentsDescription: LocalizableString = context.i18n.create("No codespaces found. Create one on github.com")

    override val loadingEnvironmentsDescription: LocalizableString = context.i18n.create("Loading codespaces...")

    override val canCreateNewEnvironments: Boolean = false

    override val isSingleEnvironment: Boolean = false

    override val additionalPluginActions: StateFlow<List<ActionDescription>> = MutableStateFlow(emptyList())

    override fun getAccountDropDown(): Nothing? = null

    override fun getOverrideUiPage(): UiPage? {
        return when (ghCli.checkAuth()) {
            is AuthStatus.Authenticated -> {
                if (!isAuthenticated) {
                    isAuthenticated = true
                    startPolling()
                }
                null
            }
            is AuthStatus.NotAuthenticated, is AuthStatus.NotInstalled -> {
                // Return a simple setup page
                null // For now, return null - users must auth via CLI
            }
        }
    }

    override fun getNewEnvironmentUiPage(): UiPage? = null

    override fun setVisible(visibility: ProviderVisibilityState) {
        when (visibility) {
            ProviderVisibilityState.Visible -> {
                if (isAuthenticated) {
                    startPolling()
                }
            }
            ProviderVisibilityState.Hidden -> {
                pollingJob?.cancel()
            }
        }
    }

    override suspend fun handleUri(uri: URI): Boolean {
        val params = parseUriParams(uri.fragment ?: "")
        val codespaceName = params["codespace"] ?: return false

        context.logger.info { "Handling URI for codespace: $codespaceName" }

        val environment = environmentCache[codespaceName]
            ?: run {
                refreshCodespaces()
                environmentCache[codespaceName]
            }

        return if (environment != null) {
            environment.connectionRequest.value = true
            true
        } else {
            context.logger.warn { "Could not find codespace: $codespaceName" }
            false
        }
    }

    override fun close() {
        pollingJob?.cancel()
        environmentCache.values.forEach { it.dispose() }
        environmentCache.clear()
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = context.scope.launch {
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

    private fun refreshCodespaces() {
        val result = ghCli.listCodespaces()

        result.onSuccess { codespaces ->
            val environments = codespaces.map { codespace ->
                environmentCache.getOrPut(codespace.name) {
                    CodespacesRemoteEnvironment(codespace, ghCli, context)
                }.also { env ->
                    env.update(codespace)
                }
            }

            val currentNames = codespaces.map { it.name }.toSet()
            environmentCache.keys.removeIf { it !in currentNames }

            _environments.value = LoadableState.Value(environments)
        }.onFailure { error ->
            context.logger.error(error) { "Failed to list codespaces" }
            _environments.value = LoadableState.Error(error.message ?: "Failed to load codespaces")
        }
    }

    private fun parseUriParams(fragment: String): Map<String, String> {
        return fragment.split('&')
            .mapNotNull { param ->
                val parts = param.split('=', limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else null
            }
            .toMap()
    }

    companion object {
        private const val POLL_INTERVAL_MS = 5000L

        private const val GITHUB_ICON_SVG = """
            <svg viewBox="0 0 16 16" fill="currentColor">
                <path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0016 8c0-4.42-3.58-8-8-8z"/>
            </svg>
        """.trimIndent()
    }
}
