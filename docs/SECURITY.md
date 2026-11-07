# Security model

## Trust boundaries

- Runtime archives are untrusted input.
- Imported projects and uploaded files are untrusted input.
- The Android app sandbox is the primary isolation boundary.
- Services bind loopback by default. LAN exposure is opt-in and not enforced inside third-party runtime configs.

## Controls

- `PathGuard` canonicalizes paths and verifies containment for projects, logs, config and runtime extraction.
- `RuntimeInstaller` rejects absolute paths, traversal, oversized archives, invalid manifests and non-AArch64 ELF entrypoints before activation. ZIP symlink entries are extracted as ordinary files; no symbolic links are created or followed.
- SHA-256 verification is available and can be required at import.
- `targetSdk 28` permits `execve` from app-private storage. This is deliberate and incompatible with Play Store policy expectations.
- Database passwords are random, AES-GCM encrypted and held in the Android Keystore. They are not written to preferences, config files or logs.
- `network_security_config.xml` blocks cleartext except for `localhost` and `127.0.0.1`.
- Android backup and device transfer are disabled for app data.

## Known risks and limitations

- Imported runtimes are native code with the app's UID. A malicious bundle gains the app's sandbox access, including projects, logs and Keystore-mediated secrets. Only install bundles you built or trust, and verify their SHA-256 out of band.
- SHA-256 is user supplied. There is no signature chain, repository or automatic update mechanism.
- The app does not sandbox individual services from each other; all run as the same Android UID.
- No root isolation, SELinux policy customization or network namespace isolation is provided.
- LAN mode exposes services to the local network if the runtime config binds to `0.0.0.0`. Authentication and TLS are the responsibility of the runtime configuration.
- The terminal is not a shell. It parses input and executes allowlisted binaries, but those binaries may still access anything the app UID can access.
- `targetSdk 28` may be blocked by future Android releases or OEM policies. Sideloading may require enabling installation from unknown sources.

## Reporting

This is a personal project. Do not expose the app or its services to untrusted networks without additional hardening.
