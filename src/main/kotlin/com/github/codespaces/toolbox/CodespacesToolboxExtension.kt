package com.github.codespaces.toolbox

import com.jetbrains.toolbox.api.core.ServiceLocator
import com.jetbrains.toolbox.api.remoteDev.RemoteDevExtension
import com.jetbrains.toolbox.api.remoteDev.RemoteProvider

/**
 * Entry point for the GitHub Codespaces Toolbox plugin.
 * This extension is loaded by JetBrains Toolbox and provides
 * remote development environments backed by GitHub Codespaces.
 */
class CodespacesToolboxExtension : RemoteDevExtension {

    override fun createRemoteProviderPluginInstance(serviceLocator: ServiceLocator): RemoteProvider {
        // Get required services from Toolbox
        val logger = serviceLocator.getService(com.jetbrains.toolbox.api.core.diagnostics.Logger::class.java)
        val httpClient = serviceLocator.getService(com.jetbrains.toolbox.api.core.http.HttpClient::class.java)
        val uiScope = serviceLocator.getService(com.jetbrains.toolbox.api.ui.UiScope::class.java)
        val remoteDevService = serviceLocator.getService(com.jetbrains.toolbox.api.remoteDev.RemoteDevService::class.java)
        val settingsStore = serviceLocator.getService(com.jetbrains.toolbox.api.core.settings.SettingsStore::class.java)

        // Create our context with all dependencies
        val context = CodespacesContext(
            logger = logger,
            httpClient = httpClient,
            uiScope = uiScope,
            remoteDevService = remoteDevService,
            settingsStore = settingsStore
        )

        return CodespacesRemoteProvider(context)
    }
}
