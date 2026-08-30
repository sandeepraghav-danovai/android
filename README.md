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
```

## Prerequisites

- **Android Studio** Ladybug (2024.2) or newer — download from developer.android.com if you don't
  have it. Any reasonably recent version will happily open this project; if it offers to upgrade
  the Gradle/AGP versions pinned here, accepting is fine.
- JDK 17 (Android Studio bundles its own, so you normally don't need to install one separately).
- A Samsung Galaxy S25 with **Developer options → USB debugging** enabled (Settings → About phone
  → tap "Build number" 7 times → back → Developer options → USB debugging on), and a USB-C cable,
  or Wi-Fi debugging on the same network as your computer.

## Build & run on your S25

1. Open Android Studio → **Open** → select this `android/` folder.
2. Let Gradle sync (first sync downloads dependencies — needs internet, and can take a few
   minutes).
3. Plug in the S25, accept the "Allow USB debugging" prompt on the phone, confirm it shows up in
   Android Studio's device dropdown (top toolbar).
4. Click **Run ▶** (or `Shift+F10`). This builds a debug APK, installs it, and launches it.

Command-line equivalent, from this folder, with the phone connected:

```bash
./gradlew installDebug
```

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

## Building a signed release APK (optional, for a permanent install)

A debug build works fine long-term for personal use, but if you want a release build:

```bash
keytool -genkeypair -v -keystore passvault-release.keystore -alias passvault -keyalg RSA -keysize 2048 -validity 10000
```

Then in Android Studio: **Build → Generate Signed Bundle / APK → APK**, point it at that keystore,
and install the resulting APK the same way (`adb install app-release.apk` or drag it onto the
device in Android Studio's Device Explorer / Files app).

## Known limitations, stated plainly

- **argon2kt API surface**: `CryptoManager.kt` calls `Argon2Kt().hash(...)` with the parameter
  names current as of writing. If Android Studio flags a mismatch on first sync (libraries do
  rename things), check the [argon2kt README](https://github.com/lambdapioneer/argon2kt) — it's a
  tiny, stable API, a two-line fix if anything shifted.
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
# android
