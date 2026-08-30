# PassVault

A local-first, encrypted password manager for Android. Phase 1 (this build): everything lives
on-device, encrypted, unlockable only with your master password. Phase 2 (not built yet, see
below): daily sync of the encrypted vault file to Google Drive.

## Design

**Key hierarchy** (same shape as Bitwarden/1Password):

- Your master password is run through **Argon2id** (`com.lambdapioneer.argon2kt`) with a random
  per-vault salt to derive a Key Encryption Key (KEK). The password itself is never stored.
- A random 256-bit Data Encryption Key (DEK) is generated once, wrapped (AES-256-GCM) with the
  KEK, and that wrapped blob is what's persisted — not the KEK.
- Every entry's password/notes are individually encrypted with the DEK, unique IV per field.
- Unlocking = re-derive KEK from the typed password → unwrap DEK → hold DEK in memory only for
  the session (`SessionManager`). Never written to disk. Cleared on lock, background, or timeout.
- A small "canary" (a known plaintext encrypted with the DEK) lets the app tell "wrong password"
  from "corrupt vault" without ever comparing plaintext passwords.

**Storage**: two files make up a vault —
[`vault_header.json`](app/src/main/java/com/sandeepraghav/passvault/data/VaultHeader.kt) (salts,
wrapped keys, recovery config) and `passvault.db` (Room/SQLite — category/title/username in the
clear for list/search UX, `password`/`notes` columns store ciphertext only). Both live in the
app's private storage (`allowBackup="false"` — Android's own cloud backup is disabled; the *only*
backup path is the explicit "Export vault backup" button in Settings, which shares both files
as-is, still fully encrypted).

There are two independent, opt-in recovery paths — set up either or both in Settings. Both end
the same way: the app unlocks a session using the escrowed DEK, then makes you set a brand-new
master password (`VaultRepository.setNewPasswordAfterRecovery`, identical to a normal password
change — the DEK itself never changes, so entries are never re-encrypted and every other escrow
stays valid).

- **SMS + email dual approval**: since this app has no server, an OTP can't itself be the
  cryptographic key (nothing to check a freshly-generated one-time code against without a
  server). So instead: a persistent `RecoveryKey` is generated once when you enable this, wraps
  the DEK, and is itself kept behind an Android Keystore (hardware-backed, device-bound) key.
  Requesting recovery sends one 8-character code via SMS to your registered phone and another via
  email (SMTP, using an app password you provide); entering both correctly is an **app-level
  gate** before the RecoveryKey is ever touched — see the doc comment in
  [`RecoveryManager.kt`](app/src/main/java/com/sandeepraghav/passvault/recovery/RecoveryManager.kt)
  for the honest threat-model caveat (it protects against someone else trying to reset your
  vault, not against a fully compromised, already-unlocked device). It's also device-bound: it
  only works on this phone.

- **One-time recovery key**: generated on demand in Settings, shown to you exactly once, and
  never stored anywhere on the device — only the DEK wrapped by its raw bytes is
  ([`VaultHeader.recoveryKitWrappedDek`](app/src/main/java/com/sandeepraghav/passvault/data/VaultHeader.kt)).
  Save it somewhere durable (password manager, printed copy). Because the app never holds the
  actual key, this one has no device-binding and no "gate" to bypass — whoever has the key and a
  copy of the vault files (e.g. from a Drive backup) can recover, on any device, with nothing
  else required. That also means losing the saved key is unrecoverable, same as losing your
  master password; regenerating in Settings replaces it and immediately invalidates the old one.

**Hardening**: `FLAG_SECURE` blocks screenshots/recording/recents-thumbnail app-wide · vault locks
the instant the app backgrounds (`MainActivity.onStop`) · viewing or copying a password requires
re-entering the master password (`ReAuthDialog`) · clipboard auto-clears ~25s after a copy ·
`allowBackup="false"` so Android never silently clouds your data.

## Project structure

```
app/src/main/java/com/sandeepraghav/passvault/
  crypto/        Argon2id KDF, AES-GCM, Android Keystore wrapper
  data/          Room entities/DAOs, the vault header file
  session/       In-memory unlock state + auto-lock
  repository/    Vault operations (create/unlock/CRUD/recovery/backup)
  recovery/      SMS + email dual-approval recovery flow
  navigation/    Compose Navigation graph
  ui/            One package per screen (setup, lock, recovery, home, category, entry, settings)

scripts/
  setup-emulator.sh   One-time provisioning of the Galaxy S25 AVD (idempotent)
  run.sh              Boot emulator + build + install + launch

tools/emulator/
  samsung_galaxy_s25-device.xml   S25 hardware profile (device schema v8 — see note below)
  skins/samsung_galaxy_s25/       Device-frame art + layout for the emulator window

tools/branding/
  generate_assets.py              Rebuilds every icon/logo resource from danovAI.png
```

## Prerequisites

- **Android Studio** Ladybug (2024.2) or newer — download from developer.android.com if you don't
  have it. Any reasonably recent version will happily open this project; if it offers to upgrade
  the Gradle/AGP versions pinned here, accepting is fine.

- **JDK 17 — and this one has a real trap.** The Gradle wrapper here is pinned to Gradle 8.9,
  which *cannot run on Java 22 or newer*, while recent Android Studio bundles a **Java 25** JBR.
  If Gradle is left pointing at the bundled JBR, sync and Run fail with a baffling error whose
  entire message is the version number (`* What went wrong: 25.0.2`). Fix it once:

  ```bash
  brew install --cask temurin@17        # if you don't already have a JDK 17
  ```

  Then in Studio: **Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK**
  → select the 17 entry. (This is stored per-project in `.gradle/config.properties`, which is
  gitignored, so it is a per-machine setting. The CLI scripts below locate JDK 17 on their own and
  refuse to run on a newer one, so they are immune to this.)

- **Android SDK Command-line Tools** — needed to create the emulator. Studio → **Settings →
  Languages & Frameworks → Android SDK → SDK Tools** tab → check **Android SDK Command-line Tools
  (latest)** → Apply.

- Only if running on real hardware: a Samsung Galaxy S25 with **Developer options → USB debugging**
  enabled (Settings → About phone → tap "Build number" 7 times → back → Developer options → USB
  debugging on), plus a USB-C cable, or Wi-Fi debugging on the same network as your computer.

## Running it

Two ways: a **simulated Galaxy S25** (no phone required) or a real handset.

### Option A — the Samsung Galaxy S25 emulator

Android Studio ships no Samsung device profiles, so this repo provides one. Run the one-time
provisioning script:

```bash
./scripts/setup-emulator.sh
```

It is idempotent — re-running it skips whatever already exists (pass `--force` to rebuild the AVD
from scratch). It sets up four things:

| What | Where it lands | Details |
|---|---|---|
| Android 15 system image | `$ANDROID_HOME/system-images/android-35/google_apis/arm64-v8a` | API 35, arm64 (native on Apple Silicon). ~1.5 GB, downloaded once |
| S25 hardware profile | `~/.android/devices.xml` | 6.2", **1080×2340 @ 420 dpi** (~416 ppi), Snapdragon 8 Elite, arm64 |
| S25 device-frame skin | `$ANDROID_HOME/skins/samsung_galaxy_s25` | Titanium rim, thin bezels, side keys — copied from `tools/emulator/skins/` |
| The AVD | `~/.android/avd/Samsung_Galaxy_S25_API_35.avd` | 4 GB RAM, 6 cores, 512 MB heap, 8 GB data, host GPU |

Then, any time you want to build and run:

```bash
./scripts/run.sh
```

That boots the emulator if it isn't already up, waits for Android to finish booting, builds the
debug APK, installs it, and launches `MainActivity` — the command-line equivalent of pressing Run.

| Flag | Effect |
|---|---|
| *(none)* | Build + install + launch on the S25 emulator |
| `--device` | Target an attached physical phone instead of the emulator |
| `--logcat` | Follow the app's logcat after launching |
| `--cold` | Cold boot the emulator, ignoring the saved snapshot |
| `--stop` | Shut the emulator down and exit |
| `--help` | Print the usage above |

> **Screenshots come out black — that's correct.** The app sets `FLAG_SECURE`, so
> `adb exec-out screencap` and the recents thumbnail are blank by design. Look at the emulator
> window itself; the app renders normally there.

### Option B — a physical Galaxy S25

1. Plug in the phone and accept the "Allow USB debugging" prompt.
2. `./scripts/run.sh --device`, or `./gradlew installDebug`.

### Running from Android Studio instead

1. **Open** → select this `android/` folder, and let Gradle sync (first sync needs internet).
2. Confirm the Gradle JDK is 17, per Prerequisites above — this is the single most common failure.
3. Pick **Samsung Galaxy S25 (API 35)** in the device dropdown in the top toolbar. It appears
   automatically once `setup-emulator.sh` has run; no restart needed. If it doesn't show, use
   **File → Reload All from Disk**.
4. Click **Run ▶** (or `Shift+F10`).

Two run configurations are checked into `.idea/runConfigurations/`:

- **app** — builds, installs and launches the debug build. This is the one you want.
- **app instrumented tests** — wired to the `androidTest` source set. Note that **no instrumented
  tests exist yet** (`app/src/androidTest/` isn't there and `app/src/test/` is empty), so this is
  scaffolding for when you add some, not something that does anything useful today.

`.gitignore` ignores `.idea/*` and then re-includes the parts that are portable, so a fresh clone
opens as a Gradle Android project with a working Run button rather than needing manual setup:

| Tracked | Why |
|---|---|
| `.idea/runConfigurations/` | The **app** and instrumented-test Run configurations |
| `.idea/vcs.xml` | Marks the project root as a Git checkout |
| `.idea/misc.xml` | Project type (Android) and compiler output dir |
| `.idea/gradle.xml` | Gradle linkage and test-runner setting |
| `.idea/AndroidProjectSystem.xml` | Tells Studio to use the Gradle project system |
| `.idea/codeStyles/`, `.idea/inspectionProfiles/` | Shared formatting/inspection rules, once you create them |

Still ignored, because they're per-user or per-machine: `workspace.xml` (open tabs, window layout,
run history, device selection), `caches/`, `migrations.xml`, `markdown.xml`, plus `.gradle/` and
`local.properties`, which hold absolute paths to *your* JDK and SDK.

One caveat on `.idea/gradle.xml`: it currently stores the Gradle JDK as the portable macro
`#GRADLE_LOCAL_JAVA_HOME` (which resolves via the gitignored `.gradle/config.properties`). If you
ever pick a JDK by absolute path in the Studio UI, that path lands in this tracked file — worth a
glance before committing.

Beware that `.gitignore` has **no trailing comments**: a `#` in the middle of a line becomes part
of the pattern, which silently breaks the rule. Every comment in that block sits on its own line
for exactly this reason.

### First run

1. **Create your master password.** Make it long and memorable — there's no cloud reset for it.
2. You're immediately shown a **one-time recovery key** — write it down or save it somewhere
   durable now, it will not be shown again. This is generated automatically for every new vault.
3. You land on Home with five default categories: Banks, Apps, Websites, Office, Emails. Tap "+"
   on the FAB to add more.
4. Tap a category → "+" to add an entry (title, username, password — with a generator — optional
   URL/notes).
5. Tap an entry to view it; **Show** and **Copy** each re-prompt for your master password.
6. Optional, in Settings: turn on **SMS + email recovery** — enter the phone number this device
   uses for SMS, a recovery email, and an SMTP app password (for Gmail: Google Account → Security
   → 2-Step Verification → App passwords — a 16-character password just for sending mail, not
   your real Gmail password).
7. **Settings → Export vault backup** shares both vault files (still encrypted) via Android's
   share sheet — pick "Drive" to drop a copy there manually. That's your backup today; Phase 2
   automates this daily. Keep your saved recovery key alongside these backups — a backup by
   itself is only useful for recovery if you also still have either your master password, SMS +
   email access, or the recovery key.

### How the S25 profile was built (and one non-obvious gotcha)

Only the emulator's *geometry and platform* are simulated — 1080×2340 at 420 dpi on Android 15,
which is what actually matters for laying out and testing the UI. The emulator still self-reports
`Build.MANUFACTURER=Google` / `Build.MODEL=sdk_gphone64_arm64`; spoofing those would mean launching
the emulator outside Studio's Run button, and this app never reads them, so it was left alone.

If you ever hand-edit `tools/emulator/samsung_galaxy_s25-device.xml`, keep its XML namespace at
**`sdk/devices/8`**. The bundled `cmdline-tools` understands device schemas only up to v8 while
Android Studio understands up to v10, and — the nasty part — `avdmanager` does not report a schema
it dislikes as an error. It *silently renames* the file to `devices.xml.old` and carries on, so the
profile simply appears to vanish. Schema 8 is the version both tools accept.

## Branding

The app is PassVault, published under the **DanovAI** brand. `danovAI.png` in the project root is
the master artwork — a landscape lockup (D mark + wordmark + tagline) drawn on white. Every image
resource in the app is derived from it, so that file is the only thing to replace if the logo
changes:

```bash
python3 tools/branding/generate_assets.py        # needs pillow + numpy
```

That regenerates 23 PNGs under `app/src/main/res/`. What it produces and why:

| Resource | Purpose |
|---|---|
| `mipmap-*/ic_launcher_foreground.png` | The **D mark alone** on the 108dp adaptive canvas |
| `mipmap-*/ic_launcher_monochrome.png` | Silhouette for Android 13+ themed icons |
| `mipmap-*/ic_launcher{,_round}.png` | Legacy 48dp rasters for anything wanting a non-adaptive icon |
| `drawable-nodpi/danov_ai_lockup.png` | Full lockup, light theme (Setup header, Settings About) |
| `drawable-night-nodpi/danov_ai_lockup.png` | Same lockup with the near-black type lightened for dark theme |
| `drawable-nodpi/danov_ai_mark.png` | Small D mark for the "by DanovAI" byline |

Three decisions worth knowing, because they are easy to get wrong:

- **The launcher icon is the D mark only, never the full lockup.** A landscape lockup with a
  wordmark and tagline is illegible at 48dp and would be cropped by the launcher's mask anyway.
- **The mark is scaled from its measured content radius**, not a guessed percentage. A launcher may
  mask the 108dp canvas down to a 66dp circle; the script measures how far the furthest opaque
  pixel sits from centre and sizes the mark so nothing can clip (currently 52.8dp).
- **The PNGs are truecolour on purpose.** Palette-quantising them cuts the lockup from 310 KB to
  122 KB, but it visibly bands the blue/purple gradient, so it isn't worth it. Total brand art is
  about 640 KB of the ~21 MB debug APK.

The white background of the master file is lifted into real alpha (un-premultiplying the
antialiased edges), so the artwork sits cleanly on any surface with no white fringing — which is
what makes the single lockup usable on both the light and dark app themes.

## Building a signed release APK (optional, for a permanent install)

A debug build works fine long-term for personal use, but if you want a release build:

```bash
keytool -genkeypair -v -keystore passvault-release.keystore -alias passvault -keyalg RSA -keysize 2048 -validity 10000
```

Then in Android Studio: **Build → Generate Signed Bundle / APK → APK**, point it at that keystore,
and install the resulting APK the same way (`adb install app-release.apk` or drag it onto the
device in Android Studio's Device Explorer / Files app).

**Build status**: verified end to end on this machine, not just written and hoped for — the S25
AVD boots, `./gradlew :app:installDebug` succeeds, and `MainActivity` reaches the resumed state on
the emulator with no crashes. `app/build/outputs/apk/debug/app-debug.apk` exists.

**Verified on the emulator**: `wm size` reports `1080x2340`, `wm density` reports `420`,
`ro.product.cpu.abi` is `arm64-v8a`, `ro.build.version.sdk` is `35` — i.e. the simulated panel and
platform match a real Galaxy S25.

## Known limitations, stated plainly

- **Recovery OTP strength**: two 8-character codes is meaningfully weaker than your master
  password. It's an app-level gate, not a cryptographic derivation from the codes (explained in
  `RecoveryManager.kt`). Treat it as "convenience if I still have my phone and email," not a
  substitute for remembering your master password.
- **SMS costs**: sends a real SMS via your carrier (`SmsManager`) — normal per-message rates
  apply, same as texting yourself.
- **Recovery key is a bearer secret**: unlike the SMS+email path, nothing gates it — whoever has
  the saved key (and your vault files) is in, no verification of who they are. Store it like you
  would a spare house key, not a sticky note on the monitor.
- **Phase 2 (Drive sync)** isn't built yet. The storage format (two flat files) is already shaped
  for it — a daily `WorkManager` job uploading them to Drive is a self-contained addition
  whenever you're ready for it.
