#!/bin/bash
#
# Setup script to download the Gradle wrapper JAR
#

set -e

GRADLE_VERSION="8.10"
WRAPPER_JAR="gradle/wrapper/gradle-wrapper.jar"
WRAPPER_URL="https://raw.githubusercontent.com/gradle/gradle/v${GRADLE_VERSION}/gradle/wrapper/gradle-wrapper.jar"

cd "$(dirname "$0")/.."

echo "Setting up Gradle wrapper for version ${GRADLE_VERSION}..."

# Create wrapper directory if it doesn't exist
mkdir -p gradle/wrapper

# Download the wrapper JAR if it doesn't exist
if [ ! -f "$WRAPPER_JAR" ]; then
    echo "Downloading gradle-wrapper.jar..."
    curl -fsSL "$WRAPPER_URL" -o "$WRAPPER_JAR"
    echo "Downloaded gradle-wrapper.jar"
else
    echo "gradle-wrapper.jar already exists"
fi

# Make gradlew executable
chmod +x gradlew

echo "Setup complete! You can now run ./gradlew build"
