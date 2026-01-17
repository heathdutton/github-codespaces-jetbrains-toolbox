package com.github.codespaces.toolbox

import com.jetbrains.toolbox.api.core.diagnostics.Logger
import com.jetbrains.toolbox.api.core.http.HttpClient
import com.jetbrains.toolbox.api.core.settings.SettingsStore
import com.jetbrains.toolbox.api.remoteDev.RemoteDevService
import com.jetbrains.toolbox.api.ui.UiScope

/**
 * Context object that holds all dependencies for the plugin.
 * This is passed around to avoid constructor parameter explosion.
 */
data class CodespacesContext(
    val logger: Logger,
    val httpClient: HttpClient,
    val uiScope: UiScope,
    val remoteDevService: RemoteDevService,
    val settingsStore: SettingsStore
)
