package com.github.codespaces.toolbox

import com.jetbrains.toolbox.api.core.ServiceLocator
import com.jetbrains.toolbox.api.core.diagnostics.Logger
import com.jetbrains.toolbox.api.localization.LocalizableStringFactory
import com.jetbrains.toolbox.api.remoteDev.RemoteDevExtension
import com.jetbrains.toolbox.api.remoteDev.RemoteProvider
import com.jetbrains.toolbox.api.remoteDev.states.EnvironmentStateColorPalette
import com.jetbrains.toolbox.api.remoteDev.ui.EnvironmentUiPageManager
import com.jetbrains.toolbox.api.ui.ToolboxUi
import kotlinx.coroutines.CoroutineScope

/**
 * Entry point for the GitHub Codespaces Toolbox plugin.
 * This extension is loaded by JetBrains Toolbox and provides
 * remote development environments backed by GitHub Codespaces.
 */
class CodespacesToolboxExtension : RemoteDevExtension {

    override fun createRemoteProviderPluginInstance(serviceLocator: ServiceLocator): RemoteProvider {
        val context = CodespacesContext(
            logger = serviceLocator.getService(Logger::class.java),
            ui = serviceLocator.getService(ToolboxUi::class.java),
            envPageManager = serviceLocator.getService(EnvironmentUiPageManager::class.java),
            colorPalette = serviceLocator.getService(EnvironmentStateColorPalette::class.java),
            i18n = serviceLocator.getService(LocalizableStringFactory::class.java),
            scope = serviceLocator.getService(CoroutineScope::class.java)
        )

        return CodespacesRemoteProvider(context)
    }
}
