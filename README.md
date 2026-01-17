# GitHub Codespaces for JetBrains Toolbox

<p align="center">
  <img src="https://github.githubassets.com/images/modules/logos_page/GitHub-Mark.png" width="80" alt="GitHub">
  &nbsp;&nbsp;&nbsp;
  <img src="https://resources.jetbrains.com/storage/products/toolbox/img/meta/toolbox_logo_300x300.png" width="80" alt="JetBrains Toolbox">
</p>

<p align="center">
  <strong>Connect to GitHub Codespaces directly from JetBrains Toolbox</strong>
</p>

<p align="center">
  <em>Community-maintained plugin after GitHub discontinued their official integration (January 2025)</em>
</p>

> **Note:** This plugin has only been tested on macOS. Linux and Windows support is experimental.

---

## Installation

### Prerequisites

1. [JetBrains Toolbox](https://www.jetbrains.com/toolbox-app/) 2.6.0+
2. macOS (Linux/Windows untested)
3. [GitHub CLI](https://cli.github.com/) installed and authenticated with codespace scope:
   ```bash
   gh auth login -s codespace
   ```
   Or if already logged in:
   ```bash
   gh auth refresh -h github.com -s codespace
   ```

### Quick Install

```bash
curl -fsSL https://raw.githubusercontent.com/heathdutton/github-codespaces-jetbrains-toolbox/main/install.sh | bash
```

Then restart JetBrains Toolbox.

### Manual Install

1. Download the latest `.zip` from [Releases](../../releases)
2. Extract to your plugins directory:

   | OS | Path |
   |----|------|
   | macOS | `~/Library/Caches/JetBrains/Toolbox/plugins/com.heathdutton.codespaces.toolbox/` |
   | Linux | `~/.local/share/JetBrains/Toolbox/plugins/com.heathdutton.codespaces.toolbox/` |
   | Windows | `%LocalAppData%\JetBrains\Toolbox\plugins\com.heathdutton.codespaces.toolbox\` |

3. Restart JetBrains Toolbox

---

## Features

- **Browse** all your GitHub Codespaces
- **Start/Stop** codespaces with one click
- **Connect** using any JetBrains IDE

---

## Building from Source

```bash
git clone https://github.com/heathdutton/github-codespaces-jetbrains-toolbox.git
cd github-codespaces-jetbrains-toolbox

./gradlew installPlugin   # Build and install to Toolbox
```

---

## How It Works

The plugin wraps the GitHub CLI to discover, manage, and connect to codespaces. JetBrains Toolbox handles the actual IDE remote connection over SSH.

---

## Issues & Contributing

Found a bug? [Open an issue](../../issues) - happy to look at them!

Pull requests also welcome.

## License

[MIT](LICENSE)

## Credits

- [Coder Toolbox Plugin](https://github.com/coder/coder-jetbrains-toolbox) - Reference implementation
- [JetBrains](https://www.jetbrains.com/) - Toolbox API
- [GitHub](https://github.com/) - Codespaces & CLI
