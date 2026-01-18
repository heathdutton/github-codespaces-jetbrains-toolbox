package com.github.codespaces.toolbox.cli

import com.github.codespaces.toolbox.models.Codespace
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.zeroturnaround.exec.ProcessExecutor
import org.zeroturnaround.exec.ProcessResult
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Wrapper around the GitHub CLI (gh) for codespace operations.
 */
class GhCli(
    private val ghPath: String = findGhPath()
) {
    companion object {
        private const val COMMAND_TIMEOUT_SECONDS = 60L

        /**
         * Find the gh CLI in common locations.
         * GUI apps on macOS don't inherit shell PATH, so we check common install locations.
         */
        private fun findGhPath(): String {
            val commonPaths = listOf(
                "/opt/homebrew/bin/gh",      // macOS ARM Homebrew
                "/usr/local/bin/gh",          // macOS Intel Homebrew / Linux
                "/usr/bin/gh",                // Linux package manager
                "/home/linuxbrew/.linuxbrew/bin/gh", // Linux Homebrew
                System.getenv("LOCALAPPDATA")?.let { "$it\\GitHub CLI\\gh.exe" }, // Windows
                "gh" // Fallback to PATH
            )

            for (path in commonPaths) {
                if (path != null && java.io.File(path).canExecute()) {
                    return path
                }
            }
            return "gh" // Fallback
        }
    }
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val codespaceListAdapter = moshi.adapter<List<Codespace>>(
        Types.newParameterizedType(List::class.java, Codespace::class.java)
    )

    private val codespaceAdapter = moshi.adapter(Codespace::class.java)

    /**
     * Check if gh CLI is installed and authenticated.
     */
    fun checkAuth(): AuthStatus {
        return try {
            val result = execute("auth", "status")
            if (result.exitValue == 0) {
                AuthStatus.Authenticated
            } else {
                AuthStatus.NotAuthenticated(result.outputString())
            }
        } catch (e: IOException) {
            AuthStatus.NotInstalled
        }
    }

    /**
     * List all codespaces for the authenticated user.
     */
    fun listCodespaces(): Result<List<Codespace>> {
        return try {
            val result = execute(
                "codespace", "list",
                "--json", "name,displayName,state,repository,gitStatus,machineName,createdAt,lastUsedAt"
            )
            if (result.exitValue == 0) {
                val codespaces = codespaceListAdapter.fromJson(result.outputString()) ?: emptyList()
                Result.success(codespaces)
            } else {
                Result.failure(GhCliException("Failed to list codespaces: ${result.outputString()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get details for a specific codespace.
     */
    fun getCodespace(name: String): Result<Codespace> {
        return try {
            val result = execute(
                "codespace", "view",
                "-c", name,
                "--json", "name,displayName,state,repository,gitStatus,machineName,createdAt,lastUsedAt"
            )
            if (result.exitValue == 0) {
                val codespace = codespaceAdapter.fromJson(result.outputString())
                    ?: return Result.failure(GhCliException("Failed to parse codespace"))
                Result.success(codespace)
            } else {
                Result.failure(GhCliException("Failed to get codespace: ${result.outputString()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Start a codespace.
     */
    fun startCodespace(name: String): Result<Unit> {
        return try {
            val result = execute("codespace", "start", "-c", name)
            if (result.exitValue == 0) {
                Result.success(Unit)
            } else {
                Result.failure(GhCliException("Failed to start codespace: ${result.outputString()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Stop a codespace.
     */
    fun stopCodespace(name: String): Result<Unit> {
        return try {
            val result = execute("codespace", "stop", "-c", name)
            if (result.exitValue == 0) {
                Result.success(Unit)
            } else {
                Result.failure(GhCliException("Failed to stop codespace: ${result.outputString()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get SSH configuration for connecting to codespaces.
     * Returns the SSH config block that can be used to connect.
     */
    fun getSshConfig(): Result<String> {
        return try {
            val result = execute("codespace", "ssh", "--config")
            if (result.exitValue == 0) {
                Result.success(result.outputString())
            } else {
                Result.failure(GhCliException("Failed to get SSH config: ${result.outputString()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Write SSH config for codespaces to ~/.ssh/config.
     * This enables the SSH hostname resolution via gh CLI ProxyCommand.
     */
    fun writeSshConfig(): Result<Unit> {
        return getSshConfig().map { config ->
            val sshDir = java.io.File(System.getProperty("user.home"), ".ssh")
            sshDir.mkdirs()

            val configFile = java.io.File(sshDir, "config")
            val existingConfig = if (configFile.exists()) configFile.readText() else ""

            // Marker comments to identify our managed section
            val startMarker = "# --- GitHub Codespaces (managed by JetBrains Toolbox plugin) ---"
            val endMarker = "# --- End GitHub Codespaces ---"

            // Remove existing codespaces section if present
            val cleanedConfig = existingConfig
                .replace(Regex("$startMarker.*?$endMarker\\s*", RegexOption.DOT_MATCHES_ALL), "")
                .trimEnd()

            // Write new config with codespaces section
            val newConfig = buildString {
                if (cleanedConfig.isNotEmpty()) {
                    append(cleanedConfig)
                    append("\n\n")
                }
                append(startMarker)
                append("\n")
                append(config.trim())
                append("\n")
                append(endMarker)
                append("\n")
            }

            configFile.writeText(newConfig)
        }
    }

    /**
     * Get the SSH host name for a specific codespace.
     * This parses the SSH config to find the matching host entry.
     */
    fun getSshHostForCodespace(codespaceName: String): Result<String> {
        return getSshConfig().map { config ->
            // Parse SSH config to find the host entry for this codespace
            // The host name typically contains the codespace name
            val hostPattern = Regex("""Host\s+(\S*$codespaceName\S*)""")
            val match = hostPattern.find(config)
            match?.groupValues?.get(1)
                ?: throw GhCliException("Could not find SSH host for codespace: $codespaceName")
        }
    }

    private fun execute(vararg args: String): ProcessResult {
        return ProcessExecutor()
            .command(ghPath, *args)
            .readOutput(true)
            .timeout(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .execute()
    }
}

sealed class AuthStatus {
    data object Authenticated : AuthStatus()
    data class NotAuthenticated(val message: String) : AuthStatus()
    data object NotInstalled : AuthStatus()
}

class GhCliException(message: String) : Exception(message)
