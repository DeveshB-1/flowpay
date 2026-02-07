#!/bin/sh
# Gradle wrapper - downloads gradle if needed
APP_NAME="Gradle"
GRADLE_VERSION="8.11.1"
GRADLE_HOME="${HOME}/.gradle/wrapper/dists/gradle-${GRADLE_VERSION}-bin"
GRADLE_BIN="${GRADLE_HOME}/gradle-${GRADLE_VERSION}/bin/gradle"

if [ ! -f "$GRADLE_BIN" ]; then
    echo "Downloading Gradle ${GRADLE_VERSION}..."
    mkdir -p "$GRADLE_HOME"
    curl -sL "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o "/tmp/gradle-${GRADLE_VERSION}.zip"
    unzip -q -o "/tmp/gradle-${GRADLE_VERSION}.zip" -d "$GRADLE_HOME"
    rm "/tmp/gradle-${GRADLE_VERSION}.zip"
fi

exec "$GRADLE_BIN" "$@"
