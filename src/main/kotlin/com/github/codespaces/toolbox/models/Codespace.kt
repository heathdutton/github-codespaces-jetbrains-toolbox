package com.github.codespaces.toolbox.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Represents a GitHub Codespace as returned by `gh codespace list --json`.
 */
@JsonClass(generateAdapter = true)
data class Codespace(
    val name: String,
    val displayName: String? = null,
    val state: CodespaceState,
    val repository: String,
    val gitStatus: GitStatus? = null,
    val machine: Machine? = null,
    val createdAt: String? = null,
    val lastUsedAt: String? = null
) {
    /**
     * User-friendly display name for the codespace.
     */
    val friendlyName: String
        get() = displayName ?: name

    /**
     * Short repository name without owner.
     */
    val repoShortName: String
        get() = repository.substringAfterLast('/')

    /**
     * Whether the codespace is currently running and connectable.
     */
    val isRunning: Boolean
        get() = state == CodespaceState.AVAILABLE

    /**
     * Whether the codespace can be started.
     */
    val canStart: Boolean
        get() = state == CodespaceState.SHUTDOWN || state == CodespaceState.STOPPED

    /**
     * Whether the codespace can be stopped.
     */
    val canStop: Boolean
        get() = state == CodespaceState.AVAILABLE
}

/**
 * Possible states of a codespace.
 */
enum class CodespaceState {
    @Json(name = "Available") AVAILABLE,
    @Json(name = "Shutdown") SHUTDOWN,
    @Json(name = "Stopped") STOPPED,
    @Json(name = "Starting") STARTING,
    @Json(name = "Stopping") STOPPING,
    @Json(name = "Rebuilding") REBUILDING,
    @Json(name = "Unavailable") UNAVAILABLE,
    @Json(name = "Queued") QUEUED,
    @Json(name = "Provisioning") PROVISIONING,
    @Json(name = "Exporting") EXPORTING,
    @Json(name = "Updating") UPDATING,
    @Json(name = "Moved") MOVED,
    @Json(name = "Deleted") DELETED,
    @Json(name = "Pending") PENDING,
    @Json(name = "Failed") FAILED,
    @Json(name = "Archived") ARCHIVED
}

/**
 * Git status of the codespace.
 */
@JsonClass(generateAdapter = true)
data class GitStatus(
    val ref: String? = null,
    val ahead: Int = 0,
    val behind: Int = 0,
    val hasUncommittedChanges: Boolean = false,
    val hasUnpushedChanges: Boolean = false
)

/**
 * Machine type information.
 */
@JsonClass(generateAdapter = true)
data class Machine(
    val name: String,
    val displayName: String? = null,
    val cpus: Int = 0,
    val memoryInBytes: Long = 0,
    val storageInBytes: Long = 0
) {
    /**
     * Human-readable memory size.
     */
    val memoryDisplay: String
        get() = "${memoryInBytes / (1024 * 1024 * 1024)} GB"

    /**
     * Human-readable storage size.
     */
    val storageDisplay: String
        get() = "${storageInBytes / (1024 * 1024 * 1024)} GB"

    /**
     * Short description like "4 cores, 16 GB RAM".
     */
    val shortDescription: String
        get() = "$cpus cores, $memoryDisplay RAM"
}
