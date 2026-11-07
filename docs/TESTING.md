# Testing and CI

## Local commands

```bash
./gradlew test
./gradlew assembleDebug
```

Prerequisites:

- JDK 17.
- Android SDK Platform 35.
- Build Tools 35.0.0.
- NDK 27.0.12077973.
- `ANDROID_HOME` or `ANDROID_SDK_ROOT` set, or `local.properties` with `sdk.dir`.

The NDK is required at build time because `compileNativeProbe` produces the `psprobe` asset. A missing NDK fails the build instead of silently shipping an empty asset.

## Unit test coverage

- `:core`: path containment, port validation, SHA-256 helpers, token parsing, formatting.
- `:runtime`: ZIP install, checksum mismatch, traversal rejection, ELF inspection.
- `:process`: process spawn, output capture, stop behavior.
- `:services`: dependency ordering, unavailable services, health gating, unexpected exit handling.
- `:database`: readiness checks.
- `:terminal`: built-ins, command parsing, allowlist, path guards.
- `:network`: URL and binding helpers.

## GitHub Actions

`.github/workflows/android-build.yml` runs on push to `main`, pull requests and manual dispatch:

1. JDK 17 and Gradle cache setup.
2. Android SDK packages and NDK installation.
3. `./gradlew test`.
4. `./gradlew assembleDebug`.
5. SHA-256 checksum and artifact upload.

Download `PocketServer-debug-arm64-v8a` from the run page. The artifact contains `app-debug.apk` and `app-debug-sha256.txt`.

`.github/workflows/android-release.yml` runs on `v*` tags. It requires repository secrets:

- `POCKETSERVER_KEYSTORE_BASE64` — base64 of the release keystore.
- `POCKETSERVER_KEYSTORE_PASSWORD`
- `POCKETSERVER_KEY_ALIAS`
- `POCKETSERVER_KEY_PASSWORD`

The workflow verifies the signature with `apksigner` before uploading.

## Manual device checks

1. Install the debug APK on an ARM64 Android 8+ device.
2. Open **Runtime manager** and run the native probe. Expect UID and kernel information plus `exec: app-private storage, no root`.
3. Import a runtime bundle with its SHA-256 and confirm the installed runtime appears.
4. Add service configuration files under the app's private config directory.
5. Start one service and confirm it reaches `RUNNING` only when its health probe succeeds.
6. Open `http://127.0.0.1:8080/` in the device browser after the web server and PHP-FPM are configured.
7. Stop all services from the notification action and confirm the foreground notification disappears.

## Limitations

CI verifies compilation and unit tests only. It cannot prove that third-party runtime bundles work on a specific phone. Runtime compatibility must be tested on-device with actual AArch64 Bionic builds.
