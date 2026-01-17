package com.github.codespaces.toolbox.views

import com.github.codespaces.toolbox.CodespacesContext
import com.github.codespaces.toolbox.cli.AuthStatus
import com.github.codespaces.toolbox.cli.GhCli
import com.jetbrains.toolbox.api.localization.LocalizableString
import com.jetbrains.toolbox.api.ui.components.UiPage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Setup wizard page shown when the user needs to authenticate or install gh CLI.
 */
class SetupWizardPage(
    private val onAuthenticated: () -> Unit,
    private val context: CodespacesContext,
    private val ghNotInstalled: Boolean = false
) : UiPage {

    private val ghCli = GhCli()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _title = MutableStateFlow(context.i18n.ptrl("GitHub Codespaces Setup"))
    override fun getTitle(): StateFlow<LocalizableString> = _title

    private val _description = MutableStateFlow(
        if (ghNotInstalled) {
            context.i18n.ptrl("""
                The GitHub CLI (gh) is not installed or not in your PATH.

                Please install it from: https://cli.github.com

                After installation, run: gh auth login
            """.trimIndent())
        } else {
            context.i18n.ptrl("""
                GitHub CLI is installed but not authenticated.

                Please run the following command in your terminal:

                gh auth login

                Then click "Check Authentication" below.
            """.trimIndent())
        }
    )
    override fun getDescription(): StateFlow<LocalizableString?> = _description

    /**
     * Check if authentication is now complete.
     */
    fun checkAuth() {
        scope.launch {
            when (ghCli.checkAuth()) {
                is AuthStatus.Authenticated -> {
                    withContext(Dispatchers.Main) {
                        onAuthenticated()
                    }
                }
                else -> {
                    context.logger.info { "Still not authenticated" }
                }
            }
        }
    }

    /**
     * Open the gh CLI installation page in browser.
     */
    fun openInstallPage() {
        context.logger.info { "Would open: https://cli.github.com" }
    }

    fun dispose() {
        scope.cancel()
    }
}
