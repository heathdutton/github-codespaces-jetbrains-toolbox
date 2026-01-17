#!/bin/bash
set -e

REPO="heathdutton/github-codespaces-jetbrains-toolbox"
PLUGIN_ID="com.heathdutton.codespaces.toolbox"

# Detect OS and set plugin directory and Toolbox paths
case "$(uname -s)" in
    Darwin)
        PLUGIN_DIR="$HOME/Library/Caches/JetBrains/Toolbox/plugins/$PLUGIN_ID"
        TOOLBOX_APP="/Applications/JetBrains Toolbox.app"
        TOOLBOX_PROCESS="jetbrains-toolbox"
        ;;
    Linux)
        PLUGIN_DIR="$HOME/.local/share/JetBrains/Toolbox/plugins/$PLUGIN_ID"
        TOOLBOX_APP="$HOME/.local/share/JetBrains/Toolbox/bin/jetbrains-toolbox"
        TOOLBOX_PROCESS="jetbrains-toolbox"
        ;;
    MINGW*|MSYS*|CYGWIN*)
        PLUGIN_DIR="$LOCALAPPDATA/JetBrains/Toolbox/plugins/$PLUGIN_ID"
        TOOLBOX_APP=""
        TOOLBOX_PROCESS="jetbrains-toolbox.exe"
        ;;
    *)
        echo "Unsupported OS: $(uname -s)"
        exit 1
        ;;
esac

# Check for gh CLI
if ! command -v gh &> /dev/null; then
    echo "GitHub CLI (gh) is not installed."

    # Try to install with brew
    if command -v brew &> /dev/null; then
        echo "Installing gh via Homebrew..."
        brew install gh
    else
        echo ""
        echo "Please install GitHub CLI manually:"
        echo "  https://cli.github.com/"
        echo ""
        exit 1
    fi
fi

# Check gh auth status
if ! gh auth status &> /dev/null; then
    echo "GitHub CLI is not authenticated."
    echo ""
    gh auth login -s codespace
fi

# Ensure codespace scope is granted
if ! gh auth status 2>&1 | grep -q "codespace"; then
    echo "Adding codespace scope to GitHub CLI..."
    gh auth refresh -h github.com -s codespace
fi

echo ""
echo "Installing GitHub Codespaces plugin for JetBrains Toolbox..."

# Get latest release URL
LATEST_URL=$(curl -sL "https://api.github.com/repos/$REPO/releases/latest" | grep "browser_download_url.*\.zip" | head -1 | cut -d '"' -f 4)

if [ -z "$LATEST_URL" ]; then
    echo "Failed to fetch latest release"
    exit 1
fi

# Get parent plugins directory
PLUGINS_DIR="$(dirname "$PLUGIN_DIR")"
mkdir -p "$PLUGINS_DIR"
cd "$PLUGINS_DIR"

# Clean old installation
rm -rf "$PLUGIN_ID"

# Download and extract (zip contains the plugin folder)
echo "Downloading from $LATEST_URL"
curl -sL "$LATEST_URL" -o plugin.zip
unzip -q -o plugin.zip
rm plugin.zip

echo ""
echo "Installed to: $PLUGIN_DIR"

# Restart Toolbox if running
restart_toolbox() {
    case "$(uname -s)" in
        Darwin)
            if pgrep -x "$TOOLBOX_PROCESS" > /dev/null; then
                echo ""
                echo "Restarting JetBrains Toolbox..."
                pkill -x "$TOOLBOX_PROCESS"
                sleep 1
                open "$TOOLBOX_APP"
                echo "Done!"
            else
                echo ""
                echo "Start JetBrains Toolbox to activate the plugin."
            fi
            ;;
        Linux)
            if pgrep -x "$TOOLBOX_PROCESS" > /dev/null; then
                echo ""
                echo "Restarting JetBrains Toolbox..."
                pkill -x "$TOOLBOX_PROCESS"
                sleep 1
                nohup "$TOOLBOX_APP" > /dev/null 2>&1 &
                echo "Done!"
            else
                echo ""
                echo "Start JetBrains Toolbox to activate the plugin."
            fi
            ;;
        *)
            echo ""
            echo "Restart JetBrains Toolbox to activate the plugin."
            ;;
    esac
}

restart_toolbox
