#!/bin/bash
#
# Setup script to download the Gradle wrapper JAR
# For CI, use: gradle wrapper --gradle-version 8.10
#

set -e

GRADLE_VERSION="8.10"
WRAPPER_JAR="gradle/wrapper/gradle-wrapper.jar"

cd "$(dirname "$0")/.."

echo "Setting up Gradle wrapper for version ${GRADLE_VERSION}..."

# Create wrapper directory if it doesn't exist
mkdir -p gradle/wrapper

# Check if gradle is available to generate the wrapper
if command -v gradle &> /dev/null; then
    echo "Using system Gradle to generate wrapper..."
    gradle wrapper --gradle-version "${GRADLE_VERSION}"
elif [ ! -f "$WRAPPER_JAR" ]; then
    echo "ERROR: gradle-wrapper.jar not found and Gradle is not installed."
    echo ""
    echo "Options:"
    echo "  1. Install Gradle: https://gradle.org/install/"
    echo "  2. Run: gradle wrapper --gradle-version ${GRADLE_VERSION}"
    echo "  3. In CI, use gradle/actions/setup-gradle which provides Gradle"
    exit 1
else
    echo "gradle-wrapper.jar already exists"
fi

# Make gradlew executable
chmod +x gradlew 2>/dev/null || true

echo "Setup complete! You can now run ./gradlew build"
