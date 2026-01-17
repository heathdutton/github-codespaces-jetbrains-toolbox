# GitHub Codespaces for JetBrains Toolbox

Connect to your GitHub Codespaces directly from JetBrains Toolbox.

> **Note:** This is a community-maintained plugin. GitHub discontinued their official JetBrains integration in January 2025.

## Features

- List all your GitHub Codespaces in JetBrains Toolbox
- Start and stop codespaces directly from the UI
- Connect to running codespaces with your favorite JetBrains IDE
- Uses the GitHub CLI (`gh`) for secure authentication

## Requirements

- JetBrains Toolbox App 2.6.0 or later
- GitHub CLI (`gh`) installed and authenticated
- JDK 21 (for building)

## Installation

### From JetBrains Marketplace

*Coming soon*

### Manual Installation

1. Download the latest release ZIP
2. Extract to your Toolbox plugins directory:
   - **macOS:** `~/Library/Caches/JetBrains/Toolbox/plugins/com.github.codespaces.toolbox/`
   - **Linux:** `~/.local/share/JetBrains/Toolbox/plugins/com.github.codespaces.toolbox/`
   - **Windows:** `%LocalAppData%/JetBrains/Toolbox/cache/plugins/com.github.codespaces.toolbox/`
3. Restart JetBrains Toolbox

## Building from Source

```bash
# Clone the repository
git clone https://github.com/heathdutton/github-codespaces-jetbrains-toolbox.git
cd github-codespaces-jetbrains-toolbox

# Run the setup script (downloads Gradle wrapper)
./scripts/setup.sh

# Build the plugin
./gradlew buildPlugin

# Or install directly to your Toolbox
./gradlew installPlugin
```

The plugin ZIP will be in `build/distributions/`.

## Setup

1. Install the [GitHub CLI](https://cli.github.com/)
2. Authenticate with GitHub:
   ```bash
   gh auth login
   ```
3. Open JetBrains Toolbox and look for "GitHub Codespaces" in the remote development section

## How It Works

This plugin wraps the GitHub CLI (`gh codespace`) commands to:
- Discover your codespaces via `gh codespace list`
- Start/stop codespaces via `gh codespace start/stop`
- Configure SSH connections via `gh codespace ssh --config`

The actual IDE connection is handled by JetBrains Toolbox's native remote development infrastructure.

## Contributing

Contributions are welcome! Please feel free to submit issues and pull requests.

## License

MIT License - see [LICENSE](LICENSE) for details.

## Acknowledgments

- [Coder](https://github.com/coder/coder-jetbrains-toolbox) for their excellent open-source Toolbox plugin that served as a reference implementation
- JetBrains for the Toolbox plugin API
- GitHub for the `gh` CLI
