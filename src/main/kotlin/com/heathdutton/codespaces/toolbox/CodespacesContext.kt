package com.heathdutton.codespaces.toolbox

import com.jetbrains.toolbox.api.core.ServiceLocator
import com.jetbrains.toolbox.api.core.diagnostics.Logger
import com.jetbrains.toolbox.api.localization.LocalizableStringFactory
import com.jetbrains.toolbox.api.remoteDev.states.EnvironmentStateColorPalette
import com.jetbrains.toolbox.api.remoteDev.ui.EnvironmentUiPageManager
import com.jetbrains.toolbox.api.ui.ToolboxUi
import kotlinx.coroutines.CoroutineScope

/**
 * Context object that holds all dependencies for the plugin.
 * Services are retrieved from the Toolbox ServiceLocator.
 */
data class CodespacesContext(
    val logger: Logger,
    val ui: ToolboxUi,
    val envPageManager: EnvironmentUiPageManager,
    val colorPalette: EnvironmentStateColorPalette,
    val i18n: LocalizableStringFactory,
    val scope: CoroutineScope,
    val serviceLocator: ServiceLocator
)
