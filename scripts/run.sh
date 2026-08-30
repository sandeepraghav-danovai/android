#!/usr/bin/env bash
#
# Build PassVault, boot the Samsung Galaxy S25 emulator if it isn't already running,
# install the debug APK, and launch the app. This is the command-line equivalent of
# pressing Run in Android Studio.
#
# Usage:
#   ./scripts/run.sh                  build + install + launch on the S25 emulator
#   ./scripts/run.sh --device         use an attached physical phone instead
#   ./scripts/run.sh --logcat         follow the app's logcat after launching
#   ./scripts/run.sh --cold           cold boot the emulator (ignore saved snapshot)
#   ./scripts/run.sh --stop           shut the emulator down and exit

set -euo pipefail

AVD_NAME="Samsung_Galaxy_S25_API_35"
APP_ID="com.sandeepraghav.passvault"
ACTIVITY=".MainActivity"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

USE_PHYSICAL=0; FOLLOW_LOG=0; COLD_BOOT=0; STOP_ONLY=0
for arg in "$@"; do
    case "$arg" in
        --device|--physical) USE_PHYSICAL=1 ;;
        --logcat|--log)      FOLLOW_LOG=1 ;;
        --cold)              COLD_BOOT=1 ;;
        --stop)              STOP_ONLY=1 ;;
        -h|--help)           sed -n '2,12p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) echo "Unknown option: $arg (try --help)" >&2; exit 1 ;;
    esac
done

info() { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
die()  { printf '\033[1;31m[error]\033[0m %s\n' "$*" >&2; exit 1; }

# ---------------------------------------------------------------- SDK + tools
if [[ -n "${ANDROID_HOME:-}" ]]; then
    SDK="$ANDROID_HOME"
elif [[ -f local.properties ]] && grep -q '^sdk.dir=' local.properties; then
    SDK="$(sed -n 's/^sdk.dir=//p' local.properties | head -1)"
else
    SDK="$HOME/Library/Android/sdk"
fi
[[ -d "$SDK" ]] || die "Android SDK not found at '$SDK'."

ADB="$SDK/platform-tools/adb"
EMULATOR="$SDK/emulator/emulator"
[[ -x "$ADB" ]] || die "adb not found at $ADB"

if [[ $STOP_ONLY -eq 1 ]]; then
    info "Stopping emulator"
    "$ADB" -e emu kill 2>/dev/null || echo "  (no emulator was running)"
    exit 0
fi

# ---------------------------------------------------------------- JDK
# Gradle 8.9 cannot run on Java 25, which is what Android Studio's bundled JBR ships.
# Prefer a real JDK 17; fail loudly rather than producing a confusing Gradle error.
if [[ -z "${JAVA_HOME:-}" ]]; then
    if J17="$(/usr/libexec/java_home -v 17 2>/dev/null)"; then
        export JAVA_HOME="$J17"
    else
        die "JDK 17 not found. Install it (brew install --cask temurin@17) or set JAVA_HOME."
    fi
fi
java_major="$("$JAVA_HOME/bin/java" -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -1)"
if [[ -n "$java_major" && "$java_major" -gt 21 ]]; then
    die "JAVA_HOME is Java $java_major; Gradle 8.9 needs Java 17-21. Point JAVA_HOME at JDK 17."
fi
info "JDK: $JAVA_HOME (Java $java_major)"

# ---------------------------------------------------------------- pick a target
if [[ $USE_PHYSICAL -eq 1 ]]; then
    info "Waiting for a physical device (make sure USB debugging is authorised)..."
    "$ADB" wait-for-device
else
    if "$ADB" devices | grep -q '^emulator-.*device$'; then
        info "Emulator already running"
    else
        [[ -x "$EMULATOR" ]] || die "emulator not found at $EMULATOR"
        if ! "$EMULATOR" -list-avds 2>/dev/null | grep -qx "$AVD_NAME"; then
            die "AVD '$AVD_NAME' does not exist. Run ./scripts/setup-emulator.sh first."
        fi
        boot_args=(-avd "$AVD_NAME")
        [[ $COLD_BOOT -eq 1 ]] && boot_args+=(-no-snapshot-load)
        info "Booting $AVD_NAME..."
        "$EMULATOR" "${boot_args[@]}" >/dev/null 2>&1 &
        # wait for adb to see it, then for Android itself to finish booting
        "$ADB" wait-for-device
        printf '    waiting for Android to finish booting'
        until [[ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do
            printf '.'; sleep 2
        done
        echo " ready"
    fi
fi

# ---------------------------------------------------------------- build + install
info "Building and installing the debug APK"
./gradlew :app:installDebug

# ---------------------------------------------------------------- launch
info "Launching $APP_ID"
"$ADB" shell am start -n "$APP_ID/$ACTIVITY" >/dev/null

cat <<EOF

  PassVault is running on $( [[ $USE_PHYSICAL -eq 1 ]] && echo "your device" || echo "the S25 emulator" ).

  Note: the app sets FLAG_SECURE, so 'adb exec-out screencap' returns a black image
  by design. Look at the emulator window itself.

EOF

if [[ $FOLLOW_LOG -eq 1 ]]; then
    info "Following logcat (Ctrl-C to stop)"
    pid="$("$ADB" shell pidof "$APP_ID" | tr -d '\r')"
    if [[ -n "$pid" ]]; then
        "$ADB" logcat --pid="$pid"
    else
        "$ADB" logcat | grep -i "$APP_ID"
    fi
fi
