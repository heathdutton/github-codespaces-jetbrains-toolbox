package com.heathdutton.codespaces.toolbox.views

import com.heathdutton.codespaces.toolbox.CodespacesContext
import com.jetbrains.toolbox.api.localization.LocalizableString
import com.jetbrains.toolbox.api.ui.actions.ActionDescription
import com.jetbrains.toolbox.api.ui.actions.RunnableActionDescription
import com.jetbrains.toolbox.api.ui.components.UiField
import com.jetbrains.toolbox.api.ui.components.UiPage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.awt.Desktop
import java.net.URI

/**
 * Page displayed at the top of the environments list.
 * Provides a button to create new codespaces on GitHub.
 */
class NewCodespacePage(
    private val context: CodespacesContext
) : UiPage(MutableStateFlow(context.i18n.ptrl("GitHub Codespaces"))) {

    override val fields: StateFlow<List<UiField>> = MutableStateFlow(emptyList())

    override val actionButtons: StateFlow<List<ActionDescription>> = MutableStateFlow(
        listOf(
            object : RunnableActionDescription {
                override val label: LocalizableString = context.i18n.ptrl("New Codespace")
                override fun run() {
                    try {
                        Desktop.getDesktop().browse(URI(NEW_CODESPACE_URL))
                    } catch (e: Exception) {
                        context.logger.error(e) { "Failed to open browser" }
                    }
                }
            }
        )
    )

    companion object {
        private const val NEW_CODESPACE_URL = "https://github.com/codespaces/new"
    }
}
