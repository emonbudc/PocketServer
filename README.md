# PocketServer

PocketServer is a standalone Android web-development stack. It runs PHP, web servers and database engines directly from app-private storage without root, Termux or a desktop Linux environment.

This repository contains Phase 1: the Android application, process supervisor, health checks, runtime installer, file manager, terminal, database readiness checks and the native-execution proof.

## Current status

Implemented:

- Jetpack Compose UI with Material 3, dark/light themes and an 11-section navigation drawer.
- Foreground service that starts/stops service groups, shows a low-priority notification and persists the desired running state.
- Ordered service startup with PHP-FPM, Apache, Nginx, MariaDB and MySQL definitions.
- Real readiness gating: `RUNNING` is shown only after process spawn and a TCP/HTTP health probe.
- Automatic first-launch runtime provisioning from an HTTPS catalog. Bundles are downloaded, SHA-256 verified, and installed without user interaction.
- SHA-256-verified runtime bundle import with zip-slip, size and AArch64 ELF validation. ZIP symlink entries are extracted as regular files and never followed.
- Native probe compiled with the Android NDK, executed from app-private storage, proving unprivileged ARM64 `execve`.
- Project and file management under `htdocs`, including SAF import/export and a path-guarded text editor.
- Built-in terminal commands and allowlisted runtime binaries with dynamic `PATH`/`LD_LIBRARY_PATH`.
- Port checks, conflict detection, Keystore-backed database passwords and log viewer.
- Unit tests for core parsers, path guards, ELF inspection, runtime install, process supervision, service ordering, terminal and networking.

Not included yet:

- Runtime bundles are not embedded in the APK. The default catalog points at the repository release `runtime-bundles-v1`, built from the official Termux ARM64 packages. PHP, Nginx, MariaDB and Apache still require matching config files.
- Database initialization/bootstrap and SQL console are not implemented.
- No Play Store distribution. `targetSdk` is intentionally 28 to permit `execve` from app-private storage.
- The app does not modify runtime configuration files automatically.

## Requirements

- Android 8.0 (API 26) or newer.
- ARM64 (`arm64-v8a`) device.
- Sideloading enabled for the APK build.
- Bundled runtimes must be AArch64 Android/Bionic ELF builds with their required shared libraries. Desktop Linux or glibc builds will not run.

## Getting the APK

Every push to `main` runs `.github/workflows/android-build.yml`. Open the **Actions** tab, select the latest **Android build** run and download the `PocketServer-debug-arm64-v8a` artifact. The artifact includes the APK and its SHA-256 checksum.

Release APKs require the four signing secrets listed in `docs/TESTING.md` and are built by `android-release.yml` on tags.

## Build locally

```bash
./gradlew test
./gradlew assembleDebug
```

Local builds require JDK 17, Android SDK Platform 35, Build Tools 35.0.0 and NDK 27.0.12077973. The Gradle wrapper pins Gradle 8.10.2.

## Runtime bundles

A runtime bundle is a ZIP containing `manifest.json` at the archive root. Example:

```json
{
  "formatVersion": 1,
  "id": "php",
  "version": "8.4.1",
  "abi": "arm64-v8a",
  "entrypoints": {
    "php": "bin/php",
    "php-fpm": "sbin/php-fpm"
  }
}
```

Supported IDs currently include `php`, `apache`, `nginx`, `mariadb`, `mysql`, `node`, `python` and `git`. See `docs/RUNTIME_COMPATIBILITY.md` for the full contract.

### Automatic provisioning

On launch, PocketServer fetches `runtime-catalog.json` over HTTPS and installs any catalog bundle whose version is not already present. Each download is written to a temporary file, checked against the catalog SHA-256, then handed to `RuntimeInstaller`.

Default catalog: `https://raw.githubusercontent.com/emonbudc/PocketServer/main/runtime-catalog.json`

Published bundles: https://github.com/emonbudc/PocketServer/releases/tag/runtime-bundles-v1

The catalog entries point at real release assets built from the Termux ARM64 package repository. Rebuild and republish with the **Runtime bundles** workflow; the workflow verifies every `.deb` against the Termux package index before packaging. Override the catalog URL at build time with `POCKETSERVER_CATALOG_URL`.

## Security model

- Runtime archive paths are validated before extraction; absolute paths, traversal and oversized archives are rejected; ZIP symlink entries are never followed or recreated.
- SHA-256 verification can be enforced at import.
- Filesystem operations are confined to the app sandbox and `htdocs`.
- Database passwords are generated randomly and stored with AES-GCM using an Android Keystore key.
- LAN access is off by default and requires explicit confirmation.
- Cleartext HTTP is permitted only for loopback hosts by the network security configuration.

See `docs/SECURITY.md` for limitations and the threat model.

## Documentation

- `docs/ARCHITECTURE.md` — modules, data flow and lifecycle.
- `docs/RUNTIME_COMPATIBILITY.md` — ABI, manifest, ELF, catalog and configuration requirements.
- `docs/TESTING.md` — local and CI verification.
- `docs/SECURITY.md` — trust boundaries and known risks.
