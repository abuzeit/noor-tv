#!/bin/bash
# Builds Neuss TV and installs it on the box, on whatever address it has today.
#
#   ./deploy.sh                 find the TV on this network and install
#   ./deploy.sh 192.168.1.189   install on that address
#   SKIP_BUILD=1 ./deploy.sh    install the APK that is already built
#
# Safe to run again: it only replaces the app, and leaves the screen, the startup link,
# the saved token and everything else on the box alone.
set -e
cd "$(dirname "$0")"

ADB=${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}
JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}
ANDROID_HOME=${ANDROID_HOME:-$HOME/Library/Android/sdk}
export JAVA_HOME ANDROID_HOME
APK=app/build/outputs/apk/release/app-release.apk

# ---------------------------------------------------------------- the build
if [ "${SKIP_BUILD:-}" != 1 ]; then
  echo "building..."
  ./gradlew -q assembleRelease
fi
[ -f "$APK" ] || { echo "no APK at $APK"; exit 1; }

# ------------------------------------------------------------- find the box
IP="$1"
if [ -z "$IP" ]; then
  # The address it had last, then every address on this network with the debugging port open.
  MINE=$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null)
  SUBNET=${MINE%.*}
  echo "looking for the TV on ${SUBNET:-?}.0/24 ..."
  for CANDIDATE in 192.168.1.189 $(seq 1 254 | sed "s|^|$SUBNET.|"); do
    [ -n "$SUBNET" ] || break
    if nc -z -G 1 -w 1 "$CANDIDATE" 5555 2>/dev/null; then IP=$CANDIDATE; break; fi
  done
fi
[ -n "$IP" ] || { echo "Could not find the TV. Is it on this Wi-Fi, and is network debugging still on?"; exit 1; }
D="$IP:5555"
echo "TV: $D"

"$ADB" connect "$D" >/dev/null 2>&1 || true
sleep 1
case "$("$ADB" devices)" in
  *"$D"$'\t'device*) ;;
  *) echo "The TV is not authorised for debugging. Look at the screen and allow it, then run this again."; exit 1 ;;
esac

# ---------------------------------------------------------------- install it
"$ADB" -s "$D" install -r "$APK"

# The permissions it needs, re-applied because a fresh install drops them.
"$ADB" -s "$D" shell appops set app.neusstv SYSTEM_ALERT_WINDOW allow      # put its screen up from the background
"$ADB" -s "$D" shell dumpsys deviceidle whitelist +app.neusstv >/dev/null  # not frozen by power saving
"$ADB" -s "$D" shell monkey -p app.neusstv -c android.intent.category.LEANBACK_LAUNCHER 1 >/dev/null 2>&1

# ------------------------------------------------------------------ check it
sleep 6
echo
if curl -s --max-time 6 "http://$IP:8080/status"; then
  echo
  echo "Done. Phone page: http://$IP:8080"
else
  echo "Installed, but the server did not answer yet — give it a few seconds and check http://$IP:8080"
fi
