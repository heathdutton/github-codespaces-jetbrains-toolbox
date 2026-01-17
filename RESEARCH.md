# GitHub Codespaces JetBrains Plugin Research

> **Status:** This project is now `github-codespaces-jetbrains-toolbox` - a **Toolbox App plugin** (not Gateway, which is being deprecated).

## Background

The official GitHub Codespaces plugin for JetBrains Gateway has been discontinued and removed from the JetBrains Marketplace. This document captures research findings for building a replacement.

## Original Plugin Details

| Property | Value |
|----------|-------|
| Plugin ID | `com.github.codespaces.jetbrains.gateway` |
| Plugin Name | GitHub Codespaces |
| Marketplace ID | 20060 |
| Last Version | 0.4.0.1360 |
| Last Release | September 20, 2023 |
| Compatibility | JetBrains Gateway 2023.1 - 2023.2.4 (build range 231-232) |
| Vendor | GitHub |

## Timeline

- **November 2022**: GitHub announced Codespaces + JetBrains integration (public beta)
- **September 2023**: Last plugin update (v0.4.0.1360)
- **December 2023**: Plugin stopped working with Gateway 2023.3+
- **November 12, 2024**: GitHub archived the feedback repository
- **January 2025**: GitHub staff confirmed official discontinuation

## Why It Failed

1. JetBrains Gateway updates broke compatibility (2023.3+)
2. GitHub never released updated versions
3. Plugin was closed-source, preventing community maintenance
4. GitHub made a business decision to discontinue JetBrains support

## What The Plugin Did

Based on community reports, the plugin provided:

1. **Codespace Discovery** - Listed user's codespaces from GitHub
2. **Authentication** - OAuth flow with GitHub
3. **Connection Management** - Established SSH tunnels to codespaces
4. **IDE Backend Deployment** - Coordinated JetBrains backend installation on codespace
5. **Port Forwarding** - Managed forwarded ports from the codespace

## GitHub CLI Primitives

The `gh codespace` CLI provides the building blocks:

```bash
# List codespaces
gh codespace list

# Get SSH config for all codespaces
gh codespace ssh --config

# SSH directly to a codespace
gh codespace ssh -c <codespace-name>

# List forwarded ports
gh codespace ports -c <codespace-name>

# Forward a port
gh codespace ports forward <local>:<remote> -c <codespace-name>

# Get codespace details (JSON)
gh codespace view -c <codespace-name> --json name,state,repository,gitStatus,machine

# Start/stop codespaces
gh codespace start -c <codespace-name>
gh codespace stop -c <codespace-name>
```

## SSH Workaround (Current State)

Users can connect manually via Gateway's SSH provider:

```bash
# Add codespace SSH config
gh codespace ssh --config >> ~/.ssh/config

# Connect using:
# - Host: codespace name from config
# - Username: codespace (not vscode)
```

Limitations of manual approach:
- No automatic codespace discovery in Gateway UI
- Must manually manage SSH config
- No integrated start/stop controls
- Port forwarding requires separate terminal

## JetBrains Gateway Plugin Development

### Resources

- [Gateway SDK Documentation](https://plugins.jetbrains.com/docs/intellij/gateway.html)
- [Remote Development Overview](https://www.jetbrains.com/help/idea/remote-development-overview.html)
- [IntelliJ Platform Plugin SDK](https://plugins.jetbrains.com/docs/intellij/welcome.html)
- [Gateway Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template)

### Key Extension Points

Gateway plugins implement connection providers:

```kotlin
// Main extension point
com.jetbrains.gateway.api.GatewayConnectionProvider

// For listing remote environments
com.jetbrains.gateway.api.GatewayRecentConnectionsProvider
```

### Build System

- Gradle with `intellij-platform-gradle-plugin`
- Kotlin (preferred) or Java
- Target platform: Gateway (not full IDE)

## Architecture Considerations

### Authentication

Options:
1. Use `gh auth status` to check existing auth
2. Trigger `gh auth login` if needed
3. Could also use GitHub OAuth directly via device flow

### Codespace Management

```
┌─────────────────────────────────────────────────────────┐
│                    Gateway Plugin                        │
├─────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────┐ │
│  │ Codespace   │  │ Connection  │  │ Settings/Auth   │ │
│  │ List View   │  │ Manager     │  │ Manager         │ │
│  └──────┬──────┘  └──────┬──────┘  └────────┬────────┘ │
│         │                │                   │          │
│         └────────────────┼───────────────────┘          │
│                          │                              │
│                    ┌─────▼─────┐                        │
│                    │  gh CLI   │                        │
│                    │  Wrapper  │                        │
│                    └─────┬─────┘                        │
└──────────────────────────┼──────────────────────────────┘
                           │
                     ┌─────▼─────┐
                     │ gh binary │
                     └───────────┘
```

### Connection Flow

1. User selects codespace from list
2. Plugin checks codespace state, starts if needed
3. Plugin configures SSH connection via `gh codespace ssh --config`
4. Gateway connects via SSH backend
5. Gateway deploys IDE backend to codespace
6. User works in IDE

## Top Reference Plugins (Exemplars)

### Primary Reference: Coder Toolbox Plugin (NEW - Recommended)

| Property | Value |
|----------|-------|
| Repository | [coder/coder-jetbrains-toolbox](https://github.com/coder/coder-jetbrains-toolbox) |
| Plugin ID | `com.coder.toolbox` |
| Target | JetBrains Toolbox App 2.6+ |
| Language | Kotlin (98.8%) |
| License | MIT |

**Why it's the best reference for our plugin:**
- Targets the new Toolbox platform (not deprecated Gateway)
- Most recent architecture patterns
- Similar use case (connecting to remote dev environments)
- MIT licensed, actively maintained by Coder

**Source Structure:**
```
src/main/kotlin/com/coder/toolbox/
├── browser/                    # Browser integration
├── cli/                        # CLI interaction (like gh)
├── models/                     # Data models
├── plugin/                     # Core plugin impl
├── sdk/                        # SDK integration
├── settings/                   # Configuration
├── store/                      # Data persistence
├── util/                       # Utilities
├── views/                      # UI components
├── CoderRemoteEnvironment.kt   # Environment definition
├── CoderRemoteProvider.kt      # Provider logic
├── CoderToolboxContext.kt      # App context/lifecycle
└── CoderToolboxExtension.kt    # Main extension entry point
```

**Key Dependencies:**
- OkHttp (HTTP client)
- Moshi (JSON serialization)
- Retrofit (REST API)
- BouncyCastle (crypto for GPG verification)
- Process execution utilities

---

### Legacy Reference: Coder Gateway Plugin (for comparison)

| Property | Value |
|----------|-------|
| Repository | [coder/jetbrains-coder](https://github.com/coder/jetbrains-coder) |
| Plugin ID | `com.coder.gateway` |
| Marketplace ID | 19620 |
| Language | Kotlin (100%) |
| Maturity | 66 releases, 895 commits, 21 contributors |
| License | MIT |

**Why it's the best reference:**
- Most mature and actively maintained Gateway plugin
- Clean architecture with well-organized source structure
- Handles similar use case (connecting to remote dev environments)
- Comprehensive implementation of all Gateway extension points
- MIT licensed, freely usable as reference

**Source Structure:**
```
src/main/kotlin/com/coder/gateway/
├── cli/                    # CLI interaction
├── help/                   # Help system
├── icons/                  # UI icons
├── models/                 # Data models
├── sdk/                    # SDK integration
├── services/               # Business logic
├── settings/               # Configuration
├── util/                   # Utilities
├── views/                  # UI components
├── CoderGatewayBundle.kt           # i18n resources
├── CoderGatewayConnectionProvider.kt  # Connection handling
├── CoderGatewayConstants.kt        # Constants
├── CoderGatewayMainView.kt         # Main UI (GatewayConnector)
├── CoderRemoteConnectionHandle.kt  # Connection lifecycle
├── CoderSettingsConfigurable.kt    # Settings panel
└── CoderSupportedVersions.kt       # Version compat
```

### 2. Red Hat Dev Spaces Plugin

| Property | Value |
|----------|-------|
| Repository | [redhat-developer/devspaces-gateway-plugin](https://github.com/redhat-developer/devspaces-gateway-plugin) |
| Plugin ID | `com.redhat.devtools.gateway` |
| Language | Kotlin (100%) |
| License | EPL-2.0 |

**Useful for:**
- Clean, minimal implementation
- Good example of URI scheme handling (`jetbrains-gateway://connect#type=devspaces&...`)
- Enterprise authentication patterns

### 3. Salesforce Pomerium Tunneler

| Property | Value |
|----------|-------|
| Repository | [salesforce/secure-pomerium-tunneler](https://github.com/salesforce/secure-pomerium-tunneler) |
| Language | Kotlin (100%) |
| License | BSD-3-Clause |

**Useful for:**
- Tunneling/proxy patterns
- Security-focused authentication
- Clean GatewayConnector + GatewayConnectionProvider implementation

## Gateway Plugin Architecture (Deep Dive)

### How JetBrains Gateway Works

Gateway operates on a **client-server model**:

```
┌─────────────────────────────────────────────────────────────────┐
│                        Local Machine                             │
│  ┌─────────────────────┐    ┌─────────────────────────────────┐ │
│  │   JetBrains Gateway │    │      JetBrains Client           │ │
│  │   (Thin Launcher)   │───▶│  (Full IDE UI, runs locally)    │ │
│  └─────────────────────┘    └──────────────┬──────────────────┘ │
└────────────────────────────────────────────┼────────────────────┘
                                             │ SSH / Lightweight Protocol
                                             ▼
┌────────────────────────────────────────────────────────────────┐
│                     Remote Machine (Codespace)                  │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                   IDE Backend (Headless)                 │   │
│  │  - Language processing      - Code indexing              │   │
│  │  - Inspections/analysis     - All heavy computation      │   │
│  └─────────────────────────────────────────────────────────┘   │
└────────────────────────────────────────────────────────────────┘
```

The protocol uses "lightweight view models" for responsive UX with minimal network overhead.

### Key Extension Points

All public API lives in `com.jetbrains.gateway.api` package.

#### 1. GatewayConnector (UI Provider)

Provides the welcome screen tab/tile for your service:

```kotlin
interface GatewayConnector {
    fun getConnectorId(): String           // Unique identifier
    fun getIcon(): Icon                    // Branding icon
    fun getTitle(): String                 // Display title
    fun getDescription(): String           // Short description
    fun isAvailable(): Boolean             // Always show? (usually true)
    fun createView(lifetime: Lifetime): JComponent  // Main UI panel
    fun getRecentConnections(callback: SetContentCallback)  // History
    fun getActionText(): String            // Button text
    fun getDocumentationAction(): AnAction?  // Help action
}
```

#### 2. GatewayConnectionProvider (Connection Handler)

Handles `jetbrains-gateway://` URI scheme connections:

```kotlin
interface GatewayConnectionProvider {
    suspend fun connect(
        parameters: Map<String, String>,
        requestor: ConnectionRequestor
    ): GatewayConnectionHandle?

    fun isApplicable(parameters: Map<String, String>): Boolean
}
```

### plugin.xml Configuration

```xml
<idea-plugin>
    <id>com.github.codespaces</id>
    <name>GitHub Codespaces</name>
    <vendor>Your Name</vendor>

    <depends>com.intellij.modules.platform</depends>
    <depends optional="true" config-file="gateway.xml">com.jetbrains.gateway</depends>

    <extensions defaultExtensionNs="com.jetbrains">
        <gateway.connector implementation="...GatewayMainView"/>
        <gateway.connectionProvider implementation="...GatewayConnectionProvider"/>
    </extensions>
</idea-plugin>
```

**Note:** The optional dependency on `com.jetbrains.gateway` is a workaround for [GTW-1528](https://youtrack.jetbrains.com/issue/GTW-1528).

### Build Configuration (build.gradle.kts)

```kotlin
plugins {
    id("org.jetbrains.intellij.platform") version "2.0.0"
    kotlin("jvm") version "1.9.23"
}

intellijPlatform {
    type = IntelliJPlatformType.Gateway  // Target Gateway, not full IDE
    version = "2024.1"  // Gateway version
}

dependencies {
    intellijPlatform {
        gateway("2024.1")
        instrumentationTools()
    }

    // Common dependencies from Coder plugin:
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.moshi:moshi:1.15.1")
    implementation("org.zeroturnaround:zt-exec:1.12")  // Process execution
}

kotlin {
    jvmToolchain(17)  // Java 17 required
}
```

### Implementation Patterns (from Coder plugin)

#### Message Localization
All UI strings use resource bundles for i18n:
```kotlin
CoderGatewayBundle.message("gateway.connector.title")
```

#### Composition-based Views
Create specialized view components rather than extending base classes:
```kotlin
fun createView(lifetime: Lifetime): JComponent {
    return CoderGatewayConnectorWizardWrapperView(lifetime)
}
```

#### Lifetime Management
Use `Lifetime` parameter for resource cleanup:
```kotlin
lifetime.onTermination { cleanup() }
```

#### Async Connection Handling
Use Kotlin coroutines with progress indicators:
```kotlin
suspend fun connect(parameters: Map<String, String>, requestor: ConnectionRequestor): GatewayConnectionHandle {
    return withContext(Dispatchers.IO) {
        indicator.text = "Connecting..."
        // connection logic
    }
}
```

## Critical: Gateway Deprecation & Toolbox Migration

**JetBrains Gateway is being deprecated.** Remote development functionality has moved to the Toolbox App as of version 2.6 (April 2025).

### What's Changing

| Aspect | Gateway (Old) | Toolbox App (New) |
|--------|---------------|-------------------|
| Status | Being deprecated | Active development |
| Remote backends | Linux only | Windows, macOS, Linux |
| URL scheme | `jetbrains-gateway://` | `jetbrains://` |
| JDK requirement | 17 | 21 |
| Plugin API | `com.jetbrains.gateway.api` | New Toolbox plugin API |
| Sample repo | N/A | [JetBrains/toolbox-remote-dev-sample](https://github.com/JetBrains/toolbox-remote-dev-sample) |

### Toolbox App 2.6+ Features

- Install, access, update, and monitor JetBrains IDEs on remote hosts
- Native OpenSSH integration (ProxyJump, MFA, reverse proxy, custom identity files)
- Import existing SSH configs from Gateway automatically
- Plugin system for providers (Coder, Gitpod, CodeCanvas)

### Migration Timeline

- **April 2025**: Toolbox App 2.6 released with remote development support
- **Ongoing**: Plugin API still evolving with breaking changes expected
- **Future**: Gateway will be fully deprecated (no specific date announced)

### Recommendation: Build for Toolbox App

Given that:
1. Gateway is being deprecated
2. Toolbox 2.6 has been available since April 2025
3. The new API is the recommended path forward
4. Toolbox supports more platforms (Windows/macOS/Linux backends)

**We should build a Toolbox App plugin, not a Gateway plugin.**

## Recommended Implementation Approach

### Target Platform Decision

| Option | Pros | Cons |
|--------|------|------|
| **Toolbox App** (Recommended) | Future-proof, better platform support, active development | API still evolving, newer ecosystem |
| Gateway (Legacy) | Stable API, more examples | Being deprecated, Linux-only backends |

**Recommendation: Build for Toolbox App** - Gateway is being deprecated and Toolbox is the future.

### Phase 1: Minimal Viable Plugin (Toolbox)
1. Use [coder/coder-jetbrains-toolbox](https://github.com/coder/coder-jetbrains-toolbox) as structural template
2. Implement `RemoteProvider` extension for GitHub Codespaces
3. Implement `RemoteEnvironment` representing a codespace
4. Wrap `gh codespace` CLI commands for all operations
5. Target Toolbox App 2.6+

### Phase 2: Core Features
1. Codespace discovery via `gh codespace list --json`
2. Start/stop controls via `gh codespace start/stop`
3. SSH connection via Toolbox's native SSH support + `gh codespace ssh --config`
4. Authentication check via `gh auth status`
5. Recent connections persistence

### Phase 3: Polish
1. Port forwarding UI
2. Machine type / repository / branch display
3. Settings for `gh` binary path
4. Codespace creation (optional)

### Technology Stack (Toolbox Plugin)
- **Language:** Kotlin (100%)
- **Build:** Gradle with Kotlin DSL
- **Target:** JetBrains Toolbox App 2.6+ (`ProductFamily.TOOLBOX`)
- **JDK:** 21 (required for Toolbox plugins)
- **HTTP:** OkHttp + Retrofit
- **JSON:** Moshi with KSP codegen
- **Process Execution:** zt-exec or ProcessBuilder

### Build Configuration (Toolbox)

```kotlin
plugins {
    kotlin("jvm") version "1.9.x"
    kotlin("plugin.serialization")
    id("com.google.devtools.ksp")  // For Moshi codegen
}

dependencies {
    // Toolbox plugin API (compile-only)
    compileOnly(libs.bundles.toolbox.plugin.api)

    // Runtime dependencies
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.moshi:moshi:1.15.1")
    ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.1")
}

kotlin {
    jvmToolchain(21)  // JDK 21 required for Toolbox
}
```

### Installation Paths (Toolbox Plugins)
```
# macOS
~/Library/Caches/JetBrains/Toolbox/plugins/<plugin-id>/

# Linux
~/.local/share/JetBrains/Toolbox/plugins/<plugin-id>/

# Windows
%LocalAppData%/JetBrains/Toolbox/cache/plugins/<plugin-id>/
```

## Open Source Alternatives (General Reference)

These projects solve similar problems and may provide architectural inspiration:

| Project | Description | JetBrains Support |
|---------|-------------|-------------------|
| [DevPod](https://github.com/loft-sh/devpod) | Open-source Codespaces alternative | Yes, via SSH |
| [Coder](https://github.com/coder/coder) | Self-hosted remote dev environments | Yes, via plugin |
| [Gitpod](https://github.com/gitpod-io/gitpod) | Cloud dev environments | Yes, via Gateway |

## References

### Reference Plugin Repositories

**Toolbox Plugins (Recommended):**
- [coder/coder-jetbrains-toolbox](https://github.com/coder/coder-jetbrains-toolbox) - **Primary reference**, MIT licensed, production-ready
- [JetBrains/toolbox-remote-dev-sample](https://github.com/JetBrains/toolbox-remote-dev-sample) - Official JetBrains sample template

**Gateway Plugins (Legacy, for reference only):**
- [coder/jetbrains-coder](https://github.com/coder/jetbrains-coder) - Gateway version (being superseded)
- [redhat-developer/devspaces-gateway-plugin](https://github.com/redhat-developer/devspaces-gateway-plugin) - Clean minimal implementation
- [salesforce/secure-pomerium-tunneler](https://github.com/salesforce/secure-pomerium-tunneler) - Security/tunneling patterns

### JetBrains Documentation

**Toolbox App (Current):**
- [Toolbox App 2.6 Remote Development Announcement](https://blog.jetbrains.com/toolbox-app/2025/04/toolbox-app-2-6-is-here-with-remote-development-support/)
- [Gateway to Toolbox Migration Guide](https://www.jetbrains.com/help/toolbox-app/jetbrains-gateway-migrations-guide.html)
- [Remote Development Overview](https://www.jetbrains.com/help/idea/remote-development-overview.html)
- [Remote Development FAQ](https://www.jetbrains.com/help/idea/faq-about-remote-development.html)

**Gateway (Legacy):**
- [Gateway Deep Dive Blog Post](https://blog.jetbrains.com/blog/2021/12/03/dive-into-jetbrains-gateway/)
- [JetBrains Gateway](https://www.jetbrains.com/remote-development/gateway/)
- [IntelliJ Platform Plugin SDK](https://plugins.jetbrains.com/docs/intellij/welcome.html)

### Community Discussions
- [Discussion #78982](https://github.com/orgs/community/discussions/78982) - Main thread about plugin deprecation (80+ upvotes)
- [Discussion #147971](https://github.com/orgs/community/discussions/147971) - Recent user reports
- [Discussion #68150](https://github.com/orgs/community/discussions/68150) - Version compatibility issues

### GitHub Resources
- [Archived Feedback Repo](https://github.com/github/codespaces-jetbrains-feedback) - Read-only
- [GitHub Docs (deprecated)](https://docs.github.com/en/codespaces/reference/using-the-github-codespaces-plugin-for-jetbrains)

### Wayback Machine
- [Archived Plugin Page (Sept 2024)](http://web.archive.org/web/20240910165024/https://plugins.jetbrains.com/plugin/20060-github-codespaces)
- [Archived Versions Page (Nov 2023)](http://web.archive.org/web/20231129012643/https://plugins.jetbrains.com/plugin/20060-github-codespaces/versions)

Note: The actual plugin ZIP files were never archived and have been deleted from JetBrains' servers.
