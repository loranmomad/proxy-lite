#!/usr/bin/env sh
# Gradle wrapper bootstrap (simplified). Use the official gradle-wrapper.jar
# for production local builds, OR use a system-installed gradle (CI does this).
DIR="$(cd "$(dirname "$0")" && pwd)"
APP_HOME="$DIR"

# Try to use a system gradle if available (CI path)
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi

# Otherwise fall back to wrapper jar if present
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
if [ -f "$CLASSPATH" ]; then
  exec java -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
fi

echo "gradle not found and gradle-wrapper.jar missing. Install Gradle 8.5+ or place the jar in gradle/wrapper/." >&2
exit 1
