package com.github.codespaces.toolbox

import com.github.codespaces.toolbox.cli.GhCli
import com.github.codespaces.toolbox.models.Codespace
import com.github.codespaces.toolbox.models.CodespaceState
import com.jetbrains.toolbox.api.remoteDev.RemoteProviderEnvironment
import com.jetbrains.toolbox.api.remoteDev.environments.RemoteEnvironmentState
import com.jetbrains.toolbox.api.ui.actions.RunnableAction
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Represents a single GitHub Codespace as a remote environment in Toolbox.
 */
class CodespacesRemoteEnvironment(
    private var codespace: Codespace,
    private val ghCli: GhCli,
    private val context: CodespacesContext
) : RemoteProviderEnvironment(codespace.name) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _state = MutableStateFlow(mapCodespaceState(codespace.state))
    override val state: StateFlow<RemoteEnvironmentState> = _state.asStateFlow()

    private val _description = MutableStateFlow(buildDescription())
    override val description: StateFlow<String> = _description.asStateFlow()

    private val _actionsList = MutableStateFlow(buildActions())
    override val actions: StateFlow<List<RunnableAction>> = _actionsList.asStateFlow()

    override val displayName: String
        get() = codespace.friendlyName

    /**
     * Update the environment with fresh codespace data.
     */
    fun update(newCodespace: Codespace) {
        codespace = newCodespace
        _state.value = mapCodespaceState(codespace.state)
        _description.value = buildDescription()
        _actionsList.value = buildActions()
    }

    /**
     * Connect to this codespace.
     */
    fun connect() {
        scope.launch {
            try {
                // Start the codespace if needed
                if (codespace.canStart) {
                    _state.value = RemoteEnvironmentState.Starting
                    ghCli.startCodespace(codespace.name).onFailure { error ->
                        context.logger.error(error) { "Failed to start codespace" }
                        _state.value = RemoteEnvironmentState.Error("Failed to start: ${error.message}")
                        return@launch
                    }
                }

                // Wait for codespace to be ready
                waitForReady()

                // Get SSH host for this codespace
                val sshHost = ghCli.getSshHostForCodespace(codespace.name).getOrThrow()

                // Log the connection attempt (actual Toolbox connection integration pending)
                context.logger.info { "Ready to connect via SSH to: $sshHost" }

            } catch (e: Exception) {
                context.logger.error(e) { "Failed to connect to codespace" }
                _state.value = RemoteEnvironmentState.Error("Connection failed: ${e.message}")
            }
        }
    }

    private suspend fun waitForReady(timeoutMs: Long = STARTUP_TIMEOUT_MS) {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val result = ghCli.getCodespace(codespace.name)
            result.onSuccess { cs ->
                if (cs.isRunning) {
                    update(cs)
                    return
                }
                update(cs)
            }
            delay(POLL_DELAY_MS)
        }
        error("Timeout waiting for codespace to start")
    }

    companion object {
        private const val STARTUP_TIMEOUT_MS = 120_000L
        private const val POLL_DELAY_MS = 2_000L
    }

    private fun buildDescription(): String {
        val parts = mutableListOf<String>()

        parts.add(codespace.repository)

        codespace.gitStatus?.ref?.let { ref ->
            parts.add("on $ref")
        }

        codespace.machine?.let { machine ->
            parts.add(machine.shortDescription)
        }

        return parts.joinToString(" | ")
    }

    private fun buildActions(): List<RunnableAction> {
        val actions = mutableListOf<RunnableAction>()

        if (codespace.canStart) {
            actions.add(RunnableAction("Start") {
                scope.launch {
                    _state.value = RemoteEnvironmentState.Starting
                    ghCli.startCodespace(codespace.name).onSuccess {
                        context.logger.info { "Started codespace: ${codespace.name}" }
                    }.onFailure { error ->
                        context.logger.error(error) { "Failed to start codespace" }
                        _state.value = RemoteEnvironmentState.Error("Failed to start")
                    }
                }
            })
        }

        if (codespace.canStop) {
            actions.add(RunnableAction("Stop") {
                scope.launch {
                    _state.value = RemoteEnvironmentState.Stopping
                    ghCli.stopCodespace(codespace.name).onSuccess {
                        context.logger.info { "Stopped codespace: ${codespace.name}" }
                    }.onFailure { error ->
                        context.logger.error(error) { "Failed to stop codespace" }
                    }
                }
            })
        }

        return actions
    }

    private fun mapCodespaceState(state: CodespaceState): RemoteEnvironmentState {
        return when (state) {
            CodespaceState.AVAILABLE -> RemoteEnvironmentState.Active
            CodespaceState.SHUTDOWN, CodespaceState.STOPPED -> RemoteEnvironmentState.Inactive
            CodespaceState.STARTING, CodespaceState.PROVISIONING, CodespaceState.QUEUED -> RemoteEnvironmentState.Starting
            CodespaceState.STOPPING -> RemoteEnvironmentState.Stopping
            CodespaceState.REBUILDING, CodespaceState.UPDATING -> RemoteEnvironmentState.Starting
            CodespaceState.UNAVAILABLE, CodespaceState.FAILED -> RemoteEnvironmentState.Error("Unavailable")
            CodespaceState.DELETED, CodespaceState.ARCHIVED, CodespaceState.MOVED -> RemoteEnvironmentState.Inactive
            CodespaceState.PENDING, CodespaceState.EXPORTING -> RemoteEnvironmentState.Starting
        }
    }

    fun dispose() {
        scope.cancel()
    }
}
