package com.github.codespaces.toolbox

import com.github.codespaces.toolbox.cli.GhCli
import com.github.codespaces.toolbox.models.Codespace
import com.github.codespaces.toolbox.models.CodespaceState
import com.jetbrains.toolbox.api.localization.LocalizableString
import com.jetbrains.toolbox.api.remoteDev.EnvironmentVisibilityState
import com.jetbrains.toolbox.api.remoteDev.RemoteProviderEnvironment
import com.jetbrains.toolbox.api.remoteDev.environments.EnvironmentContentsView
import com.jetbrains.toolbox.api.remoteDev.environments.SshEnvironmentContentsView
import com.jetbrains.toolbox.api.remoteDev.environments.SshConnectionInfo
import com.jetbrains.toolbox.api.remoteDev.states.EnvironmentDescription
import com.jetbrains.toolbox.api.remoteDev.states.RemoteEnvironmentState
import com.jetbrains.toolbox.api.ui.actions.ActionDescription
import com.jetbrains.toolbox.api.ui.actions.RunnableActionDescription
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Represents a single GitHub Codespace as a remote environment in Toolbox.
 */
class CodespacesRemoteEnvironment(
    private var codespace: Codespace,
    private val ghCli: GhCli,
    private val context: CodespacesContext
) : RemoteProviderEnvironment(codespace.name) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override var name: String = codespace.name

    override val connectionRequest: MutableStateFlow<Boolean> = MutableStateFlow(false)

    override val state: MutableStateFlow<RemoteEnvironmentState> = MutableStateFlow(
        mapCodespaceState(codespace.state)
    )

    override val description: MutableStateFlow<EnvironmentDescription> = MutableStateFlow(
        EnvironmentDescription.General(buildDescriptionText())
    )

    override val additionalEnvironmentInformation: MutableMap<LocalizableString, String> = mutableMapOf()

    override val actionsList: MutableStateFlow<List<ActionDescription>> = MutableStateFlow(buildActions())

    override val deleteActionFlow: StateFlow<(() -> Unit)?> = MutableStateFlow(null)

    /**
     * Update the environment with fresh codespace data.
     */
    fun update(newCodespace: Codespace) {
        codespace = newCodespace
        name = codespace.name
        state.value = mapCodespaceState(codespace.state)
        description.value = EnvironmentDescription.General(buildDescriptionText())
        actionsList.value = buildActions()
    }

    override suspend fun getContentsView(): EnvironmentContentsView {
        // Ensure codespace is running before providing SSH view
        if (codespace.canStart) {
            state.value = RemoteEnvironmentState.Starting
            ghCli.startCodespace(codespace.name).onFailure { error ->
                context.logger.error(error) { "Failed to start codespace" }
                state.value = RemoteEnvironmentState.Error("Failed to start: ${error.message}")
                throw error
            }
            waitForReady()
        }

        val sshHost = ghCli.getSshHostForCodespace(codespace.name).getOrThrow()
        context.logger.info { "Providing SSH connection to: $sshHost" }

        return CodespacesSshContentsView(sshHost)
    }

    override fun setVisible(visibilityState: EnvironmentVisibilityState) {
        when (visibilityState) {
            EnvironmentVisibilityState.Visible -> {
                context.logger.debug { "Environment visible: ${codespace.name}" }
            }
            EnvironmentVisibilityState.Hidden -> {
                context.logger.debug { "Environment hidden: ${codespace.name}" }
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

    private fun buildDescriptionText(): String {
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

    private fun buildActions(): List<ActionDescription> {
        val actions = mutableListOf<ActionDescription>()

        if (codespace.canStart) {
            actions.add(CodespacesAction(context, "Start") {
                state.value = RemoteEnvironmentState.Starting
                ghCli.startCodespace(codespace.name).onSuccess {
                    context.logger.info { "Started codespace: ${codespace.name}" }
                }.onFailure { error ->
                    context.logger.error(error) { "Failed to start codespace" }
                    state.value = RemoteEnvironmentState.Error("Failed to start")
                }
            })
        }

        if (codespace.canStop) {
            actions.add(CodespacesAction(context, "Stop") {
                state.value = RemoteEnvironmentState.Stopping
                ghCli.stopCodespace(codespace.name).onSuccess {
                    context.logger.info { "Stopped codespace: ${codespace.name}" }
                }.onFailure { error ->
                    context.logger.error(error) { "Failed to stop codespace" }
                }
            })
        }

        return actions
    }

    private fun mapCodespaceState(csState: CodespaceState): RemoteEnvironmentState {
        return when (csState) {
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

/**
 * SSH contents view for connecting to a codespace.
 */
private class CodespacesSshContentsView(
    private val sshHost: String
) : SshEnvironmentContentsView {
    override val sshConnectionInfo: SshConnectionInfo = CodespacesSshConnectionInfo(sshHost)
}

/**
 * SSH connection info for a codespace.
 */
private class CodespacesSshConnectionInfo(
    override val host: String
) : SshConnectionInfo {
    override val port: Int? = null
    override val userName: String? = null
}

/**
 * Action implementation for codespace operations.
 */
class CodespacesAction(
    private val context: CodespacesContext,
    private val label: String,
    private val actionBlock: suspend () -> Unit
) : RunnableActionDescription {
    
    override val name: LocalizableString = context.i18n.create(label)

    override fun run() {
        context.scope.launch {
            try {
                actionBlock()
            } catch (e: Exception) {
                context.logger.error(e) { "Action failed: $label" }
            }
        }
    }
}
