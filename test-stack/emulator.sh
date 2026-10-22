#!/usr/bin/env bash
# Points the debug app on the running emulator at the test server (`test`) or back at the real one (`live <url>`).
set -euo pipefail
ADB="${ADB:-$HOME/Android/Sdk/platform-tools/adb}"
PKG=io.github.lemonjelly76.musicplayer
PREFS=shared_prefs/music-player.xml

case "${1:-}" in
  test)
    url="http://localhost:${TEST_APP_PORT:-8092}"
    "$ADB" reverse "tcp:${TEST_APP_PORT:-8092}" "tcp:${TEST_APP_PORT:-8092}"
    "$ADB" reverse "tcp:${FAKE_SERVICES_PORT:-8091}" "tcp:${FAKE_SERVICES_PORT:-8091}" ;;
  live)
    url="${2:?give the real server address, e.g. https://pc.tailnet.ts.net:8444}" ;;
  *)
    echo "usage: $0 test | live <url>" >&2; exit 2 ;;
esac

"$ADB" shell am force-stop "$PKG"
"$ADB" shell "run-as $PKG sed -i 's|<string name=\"serverUrl\">[^<]*</string>|<string name=\"serverUrl\">$url</string>|' $PREFS"
"$ADB" shell am start -n "$PKG/.ui.MainActivity" > /dev/null
echo "The app now uses $url"
