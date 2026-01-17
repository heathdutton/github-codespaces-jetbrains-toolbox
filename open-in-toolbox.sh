#!/bin/bash
# Opens a GitHub Codespace URL in JetBrains Toolbox
#
# Usage:
#   open-in-toolbox.sh https://github.com/codespaces/opulent-enigma-7p6xg5wp7frwrv
#   open-in-toolbox.sh opulent-enigma-7p6xg5wp7frwrv
#
# You can also create a browser bookmarklet:
#   javascript:location.href='jetbrains://gateway/com.heathdutton.codespaces.toolbox?codespace='+location.pathname.split('/').pop()

PLUGIN_ID="com.heathdutton.codespaces.toolbox"

# Extract codespace name from URL or use directly
INPUT="$1"
if [[ -z "$INPUT" ]]; then
    echo "Usage: $0 <codespace-url-or-name>"
    echo ""
    echo "Examples:"
    echo "  $0 https://github.com/codespaces/my-codespace-name"
    echo "  $0 my-codespace-name"
    exit 1
fi

# Extract codespace name from URL if needed
if [[ "$INPUT" == *"github.com/codespaces/"* ]]; then
    # Remove query string and extract name
    CODESPACE_NAME=$(echo "$INPUT" | sed 's/?.*//' | sed 's|.*/||')
else
    CODESPACE_NAME="$INPUT"
fi

TOOLBOX_URL="jetbrains://gateway/${PLUGIN_ID}?codespace=${CODESPACE_NAME}"

echo "Opening codespace '$CODESPACE_NAME' in JetBrains Toolbox..."
echo "URL: $TOOLBOX_URL"

# Open the URL (works on macOS, Linux with xdg-open, Windows with start)
case "$(uname -s)" in
    Darwin)
        open "$TOOLBOX_URL"
        ;;
    Linux)
        xdg-open "$TOOLBOX_URL" 2>/dev/null || echo "Please open: $TOOLBOX_URL"
        ;;
    MINGW*|MSYS*|CYGWIN*)
        start "$TOOLBOX_URL" 2>/dev/null || echo "Please open: $TOOLBOX_URL"
        ;;
    *)
        echo "Please open this URL manually: $TOOLBOX_URL"
        ;;
esac
