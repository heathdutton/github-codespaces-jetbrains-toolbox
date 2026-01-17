#!/bin/bash
set -e

REPO="heathdutton/github-codespaces-jetbrains-toolbox"
PLUGIN_ID="com.github.codespaces.toolbox"

# Detect OS and set plugin directory
case "$(uname -s)" in
    Darwin)
        PLUGIN_DIR="$HOME/Library/Application Support/JetBrains/Toolbox/plugins/$PLUGIN_ID"
        ;;
    Linux)
        PLUGIN_DIR="$HOME/.local/share/JetBrains/Toolbox/plugins/$PLUGIN_ID"
        ;;
    MINGW*|MSYS*|CYGWIN*)
        PLUGIN_DIR="$LOCALAPPDATA/JetBrains/Toolbox/plugins/$PLUGIN_ID"
        ;;
    *)
        echo "Unsupported OS: $(uname -s)"
        exit 1
        ;;
esac

echo "Installing GitHub Codespaces plugin for JetBrains Toolbox..."

# Get latest release URL
LATEST_URL=$(curl -sL "https://api.github.com/repos/$REPO/releases/latest" | grep "browser_download_url.*\.zip" | head -1 | cut -d '"' -f 4)

if [ -z "$LATEST_URL" ]; then
    echo "Failed to fetch latest release"
    exit 1
fi

# Create plugin directory
mkdir -p "$PLUGIN_DIR"
cd "$PLUGIN_DIR"

# Clean old installation
rm -rf lib extension.json

# Download and extract
echo "Downloading from $LATEST_URL"
curl -sL "$LATEST_URL" -o plugin.zip
unzip -q -o plugin.zip
rm plugin.zip

echo ""
echo "Installed to: $PLUGIN_DIR"
echo ""
echo "Restart JetBrains Toolbox to activate the plugin."
