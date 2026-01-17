package com.github.codespaces.toolbox.cli

import com.github.codespaces.toolbox.models.Codespace
import com.github.codespaces.toolbox.models.CodespaceState
import com.github.codespaces.toolbox.models.Machine
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
    private val ghPath: String = "gh"
) {
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
                "--json", "name,displayName,state,repository,gitStatus,machine,createdAt,lastUsedAt"
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
                "--json", "name,displayName,state,repository,gitStatus,machine,createdAt,lastUsedAt"
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
            .timeout(60, TimeUnit.SECONDS)
            .execute()
    }
}

sealed class AuthStatus {
    data object Authenticated : AuthStatus()
    data class NotAuthenticated(val message: String) : AuthStatus()
    data object NotInstalled : AuthStatus()
}

class GhCliException(message: String) : Exception(message)
