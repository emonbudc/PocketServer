# Runtime compatibility

## Device requirements

- CPU: `arm64-v8a` only.
- OS: Android 8.0 (API 26) or newer.
- Execution happens from `files/pocketserver`, not `/sdcard`. Binaries stored on external storage are not executable.

## Archive contract

A runtime bundle is a ZIP with `manifest.json` at its root:

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

Validation rules enforced by `RuntimeInstaller`:

- `formatVersion` must be 1.
- `id` must be a safe lower-case identifier; the installer uses it as a directory name.
- `version` must be a safe relative segment.
- `abi` must be `arm64-v8a`.
- Entrypoint paths must be safe relative paths and must exist after extraction.
- Every entrypoint must be an ELF executable for AArch64 (`e_machine == 183`). PIC/PIE shared objects are not accepted as entrypoints.
- Archives may not contain absolute paths, `..` traversal or more than 200,000 entries. ZIP symlink entries are extracted as regular files and are never followed.
- Uncompressed size is capped at 4 GiB.

## Expected runtime IDs and entrypoints

| Service | Runtime ID | Entrypoint keys |
| --- | --- | --- |
| PHP-FPM | `php` | `php`, `php-fpm` |
| Apache | `apache` | `httpd` |
| Nginx | `nginx` | `nginx` |
| MariaDB | `mariadb` | `mariadbd`, `mariadb`, `mariadb-admin` |
| MySQL | `mysql` | `mysqld`, `mysql`, `mysqladmin` |
| Node.js | `node` | `node`, `npm`, `npx` |
| Python | `python` | `python`, `python3`, `pip`, `pip3` |
| Git | `git` | `git` |

Additional binaries are resolved from `bin/`, `sbin/` and `usr/bin/` for allowlisted names.

## Linking

Use Android/Bionic builds. The runtime environment exposes `LD_LIBRARY_PATH` containing each installed runtime's `lib`, `lib64` and `usr/lib` directories plus `/system/lib64`. Bundle every non-system shared library the executable needs. Desktop Linux glibc binaries fail with linker errors.

## Configuration

Runtime configs are not generated. Create them inside the app's private config directory, which is exposed in the database and runtime screens by absolute path. Expected paths:

| Service | Config path |
| --- | --- |
| PHP-FPM | `config/php/php-fpm.conf` |
| Apache | `config/apache/httpd.conf` |
| Nginx | `config/nginx/nginx.conf` |
| MariaDB | `config/database/mariadb.cnf` |
| MySQL | `config/database/mysql.cnf` |

The selected web server must be able to reach PHP-FPM over loopback and serve `/htdocs`. Point it at the PHP-FPM port configured in PocketServer (default 9000).

## Ports

Defaults:

- HTTP: 8080
- PHP-FPM: 9000
- MariaDB: 3306
- MySQL: 3307

The app prevents assigning the same port to two services. Loopback is the default; enabling LAN access in PocketServer does not edit runtime configs, so each server must be configured to bind `0.0.0.0` separately.

## Verification

Import a bundle with a mandatory SHA-256 checksum. The Runtime screen displays the computed digest and verification status. Use the native probe first to confirm that the device executes AArch64 binaries from app-private storage.
