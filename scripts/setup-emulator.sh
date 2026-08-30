#!/usr/bin/env bash
#
# One-time provisioning of the "Samsung Galaxy S25" emulator used to test PassVault.
#
# Creates, idempotently:
#   1. the S25 hardware profile in ~/.android/devices.xml   (Android Studio Device Manager)
#   2. the S25 device-frame skin in $SDK/skins/             (the phone body drawn around the screen)
#   3. the AVD "Samsung_Galaxy_S25_API_35"                  (Android 15 / API 35, arm64)
#
# Safe to re-run: existing pieces are detected and left alone unless --force is passed.

set -euo pipefail

AVD_NAME="Samsung_Galaxy_S25_API_35"
DEVICE_ID="samsung_galaxy_s25"
SKIN_NAME="samsung_galaxy_s25"
SYSTEM_IMAGE="system-images;android-35;google_apis;arm64-v8a"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TOOLS_DIR="$REPO_ROOT/tools/emulator"
FORCE=0
[[ "${1:-}" == "--force" ]] && FORCE=1

info()  { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
warn()  { printf '\033[1;33m[warn]\033[0m %s\n' "$*" >&2; }
die()   { printf '\033[1;31m[error]\033[0m %s\n' "$*" >&2; exit 1; }

# ---------------------------------------------------------------- locate the SDK
if [[ -n "${ANDROID_HOME:-}" ]]; then
    SDK="$ANDROID_HOME"
elif [[ -f "$REPO_ROOT/local.properties" ]] && grep -q '^sdk.dir=' "$REPO_ROOT/local.properties"; then
    SDK="$(sed -n 's/^sdk.dir=//p' "$REPO_ROOT/local.properties" | head -1)"
else
    SDK="$HOME/Library/Android/sdk"
fi
[[ -d "$SDK" ]] || die "Android SDK not found at '$SDK'. Set ANDROID_HOME or sdk.dir in local.properties."
info "Using SDK: $SDK"

SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"
AVDMANAGER="$SDK/cmdline-tools/latest/bin/avdmanager"

# ---------------------------------------------------------------- JDK for the SDK tools
# The SDK command-line tools need a JDK. Android Studio's bundled JBR is Java 25, which
# Gradle 8.9 cannot run on, so prefer a real JDK 17 when one is installed.
if [[ -z "${JAVA_HOME:-}" ]]; then
    if J17="$(/usr/libexec/java_home -v 17 2>/dev/null)"; then
        export JAVA_HOME="$J17"
    elif [[ -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ]]; then
        export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
    else
        die "No JDK found. Install Temurin 17 (brew install --cask temurin@17) or set JAVA_HOME."
    fi
fi
info "Using JDK: $JAVA_HOME"

# ---------------------------------------------------------------- 1. command-line tools
if [[ ! -x "$SDKMANAGER" ]]; then
    warn "SDK command-line tools are not installed at $SDK/cmdline-tools/latest"
    cat >&2 <<'MSG'

  Install them once, either way:
    * Android Studio -> Settings -> Languages & Frameworks -> Android SDK
      -> "SDK Tools" tab -> check "Android SDK Command-line Tools (latest)" -> Apply
    * or download commandlinetools-mac-*.zip from
      https://developer.android.com/studio#command-line-tools-only
      and unzip so that $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager exists

MSG
    die "cmdline-tools required."
fi

# ---------------------------------------------------------------- 2. system image
if "$SDKMANAGER" --list_installed 2>/dev/null | grep -q "${SYSTEM_IMAGE//;//}"; then
    info "System image already installed: $SYSTEM_IMAGE"
else
    info "Installing system image $SYSTEM_IMAGE (~1.5 GB, this takes a while)..."
    yes | "$SDKMANAGER" --licenses >/dev/null 2>&1 || true
    "$SDKMANAGER" "$SYSTEM_IMAGE"
fi

# ---------------------------------------------------------------- 3. device-frame skin
SKIN_DEST="$SDK/skins/$SKIN_NAME"
if [[ -d "$SKIN_DEST" && $FORCE -eq 0 ]]; then
    info "Skin already present: $SKIN_DEST"
else
    info "Installing S25 device-frame skin -> $SKIN_DEST"
    mkdir -p "$SKIN_DEST"
    cp "$TOOLS_DIR/skins/$SKIN_NAME/"* "$SKIN_DEST/"
fi

# ---------------------------------------------------------------- 4. hardware profile
# NOTE: the profile is pinned to device schema v8 on purpose. The bundled cmdline-tools
# dvlib understands schemas up to v8 while Android Studio understands up to v10; v9+ files
# are silently renamed to devices.xml.old by avdmanager. v8 is what both accept.
USER_DEVICES="$HOME/.android/devices.xml"
mkdir -p "$HOME/.android"

if [[ ! -f "$USER_DEVICES" ]]; then
    info "Creating $USER_DEVICES with the S25 profile"
    cp "$TOOLS_DIR/samsung_galaxy_s25-device.xml" "$USER_DEVICES"
elif grep -q "<d:id>$DEVICE_ID</d:id>" "$USER_DEVICES"; then
    info "S25 hardware profile already registered"
else
    existing_ns="$(grep -o 'sdk/devices/[0-9]*' "$USER_DEVICES" | head -1 || true)"
    if [[ "$existing_ns" != "sdk/devices/8" ]]; then
        warn "$USER_DEVICES uses schema '$existing_ns' but the S25 profile is schema 8."
        warn "Not merging automatically. Add tools/emulator/samsung_galaxy_s25-device.xml by hand,"
        warn "or move the existing file aside and re-run this script."
    else
        info "Merging the S25 profile into the existing $USER_DEVICES"
        backup="$USER_DEVICES.bak.$(date +%s)"
        cp "$USER_DEVICES" "$backup"
        block="$(mktemp)"
        sed -n '/<d:device>/,/<\/d:device>/p' "$TOOLS_DIR/samsung_galaxy_s25-device.xml" > "$block"
        awk -v block="$block" '
            /<\/d:devices>/ && !inserted {
                while ((getline line < block) > 0) print line
                close(block); inserted = 1
            }
            { print }
        ' "$backup" > "$USER_DEVICES"
        rm -f "$block"
        info "Previous file backed up to $backup"
    fi
fi

# ---------------------------------------------------------------- 5. the AVD
AVD_DIR="$HOME/.android/avd/$AVD_NAME.avd"
if [[ -d "$AVD_DIR" && $FORCE -eq 0 ]]; then
    info "AVD already exists: $AVD_NAME (re-run with --force to recreate)"
else
    info "Creating AVD $AVD_NAME"
    echo "no" | "$AVDMANAGER" create avd \
        -n "$AVD_NAME" \
        -k "$SYSTEM_IMAGE" \
        -d "$DEVICE_ID" \
        --force >/dev/null
fi

# ---------------------------------------------------------------- 6. tune the AVD hardware
CONFIG="$AVD_DIR/config.ini"
[[ -f "$CONFIG" ]] || die "Expected $CONFIG to exist after AVD creation."

info "Applying S25 hardware settings"
set_prop() {
    local key="$1" value="$2"
    if grep -q "^${key}=" "$CONFIG"; then
        # portable in-place edit (BSD + GNU sed)
        sed "s|^${key}=.*|${key}=${value}|" "$CONFIG" > "$CONFIG.tmp" && mv "$CONFIG.tmp" "$CONFIG"
    else
        printf '%s=%s\n' "$key" "$value" >> "$CONFIG"
    fi
}

set_prop "avd.ini.displayname"      "Samsung Galaxy S25 (API 35)"
set_prop "hw.lcd.width"             "1080"       # S25 panel: 1080 x 2340 @ 420dpi (6.2", ~416ppi)
set_prop "hw.lcd.height"            "2340"
set_prop "hw.lcd.density"           "420"
set_prop "hw.ramSize"               "4096"
set_prop "vm.heapSize"              "512M"
set_prop "hw.cpu.ncore"             "6"
set_prop "disk.dataPartition.size"  "8G"
set_prop "hw.gpu.enabled"           "yes"        # Metal-backed rendering on Apple Silicon
set_prop "hw.gpu.mode"              "host"
set_prop "hw.keyboard"              "yes"        # type master passwords from the host keyboard
set_prop "hw.camera.front"          "emulated"
set_prop "showDeviceFrame"          "yes"
set_prop "skin.name"                "$SKIN_NAME"
set_prop "skin.path"                "$SKIN_DEST"
set_prop "skin.dynamic"             "no"

info "Done."
echo
echo "  AVD:    $AVD_NAME"
echo "  Device: Samsung Galaxy S25 - 1080x2340 @ 420dpi, Android 15 (API 35), arm64"
echo
echo "  Launch + install + run the app:   ./scripts/run.sh"
echo "  Or pick it from the device dropdown in Android Studio and press Run."
