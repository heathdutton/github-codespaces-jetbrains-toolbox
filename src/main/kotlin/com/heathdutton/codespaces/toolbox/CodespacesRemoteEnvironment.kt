package com.heathdutton.codespaces.toolbox

import com.heathdutton.codespaces.toolbox.cli.GhCli
import com.heathdutton.codespaces.toolbox.models.Codespace
import com.heathdutton.codespaces.toolbox.models.CodespaceState
import com.jetbrains.toolbox.api.localization.LocalizableString
import com.jetbrains.toolbox.api.remoteDev.EnvironmentVisibilityState
import com.jetbrains.toolbox.api.remoteDev.RemoteProviderEnvironment
import com.jetbrains.toolbox.api.remoteDev.environments.EnvironmentContentsView
import com.jetbrains.toolbox.api.remoteDev.environments.SshEnvironmentContentsView
import com.jetbrains.toolbox.api.remoteDev.ssh.SshConnectionInfo
import com.jetbrains.toolbox.api.remoteDev.states.EnvironmentDescription
import com.jetbrains.toolbox.api.remoteDev.states.RemoteEnvironmentState
import com.jetbrains.toolbox.api.remoteDev.states.StandardRemoteEnvironmentState
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
        EnvironmentDescription.General(context.i18n.ptrl(buildDescriptionText()))
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
        description.value = EnvironmentDescription.General(context.i18n.ptrl(buildDescriptionText()))
        actionsList.value = buildActions()
    }

    override suspend fun getContentsView(): EnvironmentContentsView {
        // Ensure codespace is running before providing SSH view
        if (codespace.canStart) {
            state.value = StandardRemoteEnvironmentState.Activating
            ghCli.startCodespace(codespace.name).onFailure { error ->
                context.logger.error(error) { "Failed to start codespace" }
                state.value = StandardRemoteEnvironmentState.Unreachable
                throw error
            }
            waitForReady()
        }

        // Ensure SSH config is written for this codespace
        ghCli.writeSshConfig().onFailure { error ->
            context.logger.error(error) { "Failed to write SSH config" }
        }

        val sshHost = ghCli.getSshHostForCodespace(codespace.name).getOrThrow()
        context.logger.info { "Providing SSH connection to: $sshHost" }

        return CodespacesSshContentsView(sshHost)
    }

    override fun setVisible(visibilityState: EnvironmentVisibilityState) {
        // Handle visibility changes if needed
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

        codespace.machineName?.let { machineName ->
            parts.add(machineName)
        }

        return parts.joinToString(" | ")
    }

    private fun buildActions(): List<ActionDescription> {
        val actions = mutableListOf<ActionDescription>()

        if (codespace.canStart) {
            actions.add(CodespacesAction(context, "Start") {
                state.value = StandardRemoteEnvironmentState.Activating
                ghCli.startCodespace(codespace.name).onSuccess {
                    context.logger.info { "Started codespace: ${codespace.name}" }
                }.onFailure { error ->
                    context.logger.error(error) { "Failed to start codespace" }
                    state.value = StandardRemoteEnvironmentState.Unreachable
                }
            })
        }

        if (codespace.canStop) {
            actions.add(CodespacesAction(context, "Stop") {
                state.value = StandardRemoteEnvironmentState.Activating
                ghCli.stopCodespace(codespace.name).onSuccess {
                    context.logger.info { "Stopped codespace: ${codespace.name}" }
                    state.value = StandardRemoteEnvironmentState.Inactive
                }.onFailure { error ->
                    context.logger.error(error) { "Failed to stop codespace" }
                }
            })
        }

        return actions
    }

    private fun mapCodespaceState(csState: CodespaceState): RemoteEnvironmentState {
        return when (csState) {
            CodespaceState.AVAILABLE -> StandardRemoteEnvironmentState.Active
            CodespaceState.SHUTDOWN, CodespaceState.STOPPED -> StandardRemoteEnvironmentState.Inactive
            CodespaceState.STARTING, CodespaceState.PROVISIONING, CodespaceState.QUEUED -> StandardRemoteEnvironmentState.Activating
            CodespaceState.STOPPING -> StandardRemoteEnvironmentState.Activating
            CodespaceState.REBUILDING, CodespaceState.UPDATING -> StandardRemoteEnvironmentState.Activating
            CodespaceState.UNAVAILABLE, CodespaceState.FAILED -> StandardRemoteEnvironmentState.Unreachable
            CodespaceState.DELETED, CodespaceState.ARCHIVED, CodespaceState.MOVED -> StandardRemoteEnvironmentState.Inactive
            CodespaceState.PENDING, CodespaceState.EXPORTING -> StandardRemoteEnvironmentState.Activating
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
    override suspend fun getConnectionInfo(): SshConnectionInfo = CodespacesSshConnectionInfo(sshHost)
}

/**
 * SSH connection info for a codespace.
 * Note: The Toolbox API doesn't support setting a default project path via SshConnectionInfo.
 * Users will need to navigate to /workspaces/<repo-name> manually.
 */
private class CodespacesSshConnectionInfo(
    override val host: String
) : SshConnectionInfo {
    override val port: Int = SSH_PORT
    override val userName: String? = null

    companion object {
        private const val SSH_PORT = 22
    }
}

/**
 * Action implementation for codespace operations.
 */
class CodespacesAction(
    private val context: CodespacesContext,
    actionLabel: String,
    private val actionBlock: suspend () -> Unit
) : RunnableActionDescription {

    override val label: LocalizableString = context.i18n.ptrl(actionLabel)

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
