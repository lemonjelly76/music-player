#!/usr/bin/env bash
# Builds the Windows app on Linux: a folder with "AI Picks.exe" and its own Java runtime, zipped (ADR-016).
# jlink and jpackage come from a Windows JDK 21 run under wine, as jpackage only makes apps for the OS it runs on.
# Usage: JAVA_HOME=<a JDK 21> desktop/package-windows.sh <path to an unzipped Windows JDK 21>
set -euo pipefail

windows_jdk=$(realpath "${1:?Give the path to an unzipped Windows JDK 21}")
apps=$(cd "$(dirname "$0")/.." && pwd)
out="$apps/desktop/build/windows"
version=1.0.0

cd "$apps"
./gradlew -q -Pwindows :desktop:windowsJars

rm -rf "$out/runtime" "$out/app" "$out/AI-Picks-windows.zip"
export WINEDEBUG=-all
wine "$windows_jdk/bin/jlink.exe" --compress zip-9 --strip-debug --no-header-files --no-man-pages \
    --add-modules java.base,java.desktop,java.logging,java.management,java.naming,java.net.http,java.prefs,jdk.crypto.ec,jdk.unsupported,jdk.accessibility \
    --output "$(winepath -w "$out/runtime")"
wine "$windows_jdk/bin/jpackage.exe" --type app-image --name "AI Picks" --app-version "$version" \
    --vendor lemonjelly76 --icon "$(winepath -w "$apps/desktop/icons/app.ico")" \
    --input "$(winepath -w "$out/jars")" --main-jar desktop.jar \
    --main-class io.github.lemonjelly76.musicplayer.desktop.MainKt \
    --runtime-image "$(winepath -w "$out/runtime")" --dest "$(winepath -w "$out/app")"

(cd "$out/app" && zip -qr "$out/AI-Picks-windows.zip" "AI Picks")
echo "$out/AI-Picks-windows.zip"
