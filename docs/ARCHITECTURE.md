# Architecture

## Modules

| Module | Type | Responsibility |
| --- | --- | --- |
| `:core` | JVM | Service models, settings, ports, path guard, SHA-256, formatting. |
| `:storage` | Android library | App paths, preferences, logs, projects, Keystore secrets. |
| `:runtime` | JVM | Runtime archive validation, extraction, installation, activation. |
| `:process` | JVM | Managed native processes, log pumping, state transitions. |
| `:services` | JVM | Service definitions, dependency ordering, health probes, supervisor. |
| `:database` | JVM | Engine metadata and data-directory readiness. |
| `:terminal` | JVM | Parsed command allowlist and external binary execution. |
| `:network` | JVM | Loopback/LAN binding helpers and local URL construction. |
| `:ui` | Android library | Compose theme, components and all screens. |
| `:app` | Android application | DI container, foreground service, ViewModels, activity and navigation. |

## Application startup

1. `PocketServerApplication` creates `AppContainer`.
2. `AppContainer.initialize()` creates the private directory layout and logs device ABI information.
3. `ServiceManager.refresh()` evaluates installed runtimes, configuration files and selected web server, producing availability snapshots.
4. `MainActivity` hosts the Compose tree and obtains `ServerViewModel`, `WorkspaceViewModel` and `ToolingViewModel` from `AppViewModelFactory`.

## Service lifecycle

`ServerController` sends an intent to `ServerForegroundService`.

- `START_ALL` / `STOP_ALL` operate on the whole stack.
- `START_SERVICE`, `STOP_SERVICE` and `RESTART_SERVICE` target one `ServiceKind`.
- `ConfigStore.setDesiredRunning` records whether the user expects services to be active.
- The service promotes itself with a low-priority notification and collects `ServiceManager.snapshots` while services are active.
- If the process is recreated and `desiredRunning` plus `autoRestartAfterProcessDeath` are true, `START_STICKY` triggers a restart on the next `onStartCommand`.

`ServiceManager` starts dependencies first (PHP-FPM, MariaDB/MySQL, then the selected web server), validates configuration when a validation command exists, spawns a `ManagedProcess`, and only marks `RUNNING` after the health probe succeeds. Unexpected process exits are monitored and transition the snapshot to `FAILED` with an error message.

## Runtime install flow

1. UI receives a SAF URI and optional expected SHA-256.
2. `ToolingViewModel` copies the archive into `temp` with a 2 GiB cap.
3. `RuntimeInstaller` hashes the archive, parses `manifest.json`, validates entries, extracts into a staging directory, rejects non-AArch64 ELF entrypoints and atomically moves the tree to `packages/<id>/<version>`.
4. `active` is written with the version and entrypoints are chmod 0755.
5. `RuntimeCommandRegistry` resolves terminal commands and builds `PATH`/`LD_LIBRARY_PATH` from installed runtimes.

## Files and projects

`ProjectStore` resolves every path through `PathGuard`, creating projects under `htdocs`. Text files up to 512 KiB can be previewed and saved; import/export is capped at 64 MiB. Binary previews are detected by NUL bytes and never rendered as text.

## Native probe

`psprobe.c` is compiled by the Gradle `compileNativeProbe` task with NDK Clang into `build/generated/probeAssets/arm64-v8a/psprobe`. `NativeProbe` copies it to `files/pocketserver/bin`, marks it executable and runs it from app-private storage. Its output proves that the process model needed for imported runtimes works on the device.

## Known architectural gaps

- Runtime configuration templates and database bootstrap are manual.
- There is no package downloader or trust store beyond user-supplied checksums.
- `targetSdk 28` is required for app-private `execve`; this limits compatibility with future Android restrictions and Play policy.
