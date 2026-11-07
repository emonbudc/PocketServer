#!/usr/bin/env python3
import argparse
import hashlib
import json
import os
import re
import shutil
import stat
import subprocess
import sys
import tempfile
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path

MIRRORS = [
    "https://packages-cf.termux.dev/apt/termux-main/",
    "https://packages.termux.dev/apt/termux-main/",
]
INDEX_PATH = "dists/stable/main/binary-aarch64/Packages"
TERMUX_PREFIX = "/data/data/com.termux/files/usr"
USER_AGENT = "PocketServer-runtime-bundles/1.0"

BUNDLES = [
    {
        "id": "php",
        "packages": ["php", "php-fpm"],
        "description": "PHP CLI and PHP-FPM from Termux",
        "entrypoints": {
            "php": ["bin/php"],
            "php-fpm": ["bin/php-fpm", "sbin/php-fpm"],
        },
    },
    {
        "id": "nginx",
        "packages": ["nginx"],
        "description": "Nginx web server from Termux",
        "entrypoints": {
            "nginx": ["bin/nginx", "sbin/nginx"],
        },
    },
    {
        "id": "mariadb",
        "packages": ["mariadb"],
        "description": "MariaDB server and clients from Termux",
        "entrypoints": {
            "mariadbd": ["bin/mariadbd", "sbin/mariadbd"],
            "mariadb": ["bin/mariadb", "bin/mysql"],
            "mariadb-admin": ["bin/mariadb-admin", "bin/mysqladmin"],
        },
    },
    {
        "id": "apache",
        "packages": ["apache2"],
        "description": "Apache HTTP Server from Termux",
        "entrypoints": {
            "httpd": ["bin/httpd", "sbin/httpd"],
        },
    },
]


class BundleError(Exception):
    pass


def log(message):
    print(message, flush=True)


def request(url, destination=None):
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=120) as response:
        if response.status != 200:
            raise BundleError(f"HTTP {response.status} for {url}")
        if destination is None:
            return response.read()
        with open(destination, "wb") as output:
            shutil.copyfileobj(response, output)
    return None


def sha256_file(path):
    digest = hashlib.sha256()
    with open(path, "rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def parse_packages(text):
    packages = {}
    provides = {}
    stanzas = re.split(r"\n\s*\n", text.strip())
    for stanza in stanzas:
        fields = {}
        current = None
        for line in stanza.splitlines():
            if line.startswith((" ", "\t")) and current:
                fields[current] += "\n" + line.strip()
            elif ":" in line:
                key, value = line.split(":", 1)
                current = key.strip()
                fields[current] = value.strip()
        name = fields.get("Package")
        if not name:
            continue
        packages[name] = fields
        for provided in fields.get("Provides", "").split(","):
            provided = provided.strip().split("(")[0].strip()
            if provided:
                provides.setdefault(provided, name)
    return packages, provides


def split_dependencies(value):
    if not value:
        return []
    result = []
    for clause in value.split(","):
        alternatives = []
        for alternative in clause.split("|"):
            name = re.sub(r"\(.*?\)", "", alternative).strip().split()[0]
            if name:
                alternatives.append(name)
        if alternatives:
            result.append(alternatives)
    return result


def resolve_packages(roots, packages, provides):
    resolved = {}
    visiting = set()

    def visit(name):
        if name in resolved:
            return
        if name in visiting:
            raise BundleError(f"Dependency cycle at {name}")
        if name not in packages:
            name = provides.get(name)
        if not name or name not in packages:
            raise BundleError(f"Package not found in Termux index: {name}")
        visiting.add(name)
        for alternatives in split_dependencies(packages[name].get("Depends", "")):
            for candidate in alternatives:
                target = candidate if candidate in packages else provides.get(candidate)
                if target:
                    visit(target)
                    break
            else:
                raise BundleError(
                    f"No available dependency for {packages[name]['Package']}: {alternatives}"
                )
        visiting.remove(name)
        resolved[name] = packages[name]

    for root in roots:
        visit(root)
    return resolved


def download_package(index, destination):
    filename = index["Filename"]
    expected = index.get("SHA256", "").lower()
    for mirror in MIRRORS:
        url = urllib.parse.urljoin(mirror, filename)
        try:
            log(f"  downloading {index['Package']} {index.get('Version')} from {url}")
            request(url, destination)
            actual = sha256_file(destination)
            if expected and actual != expected:
                raise BundleError(
                    f"SHA-256 mismatch for {index['Package']}: expected {expected}, got {actual}"
                )
            return
        except Exception as exception:
            log(f"  mirror failed: {exception}")
            destination.unlink(missing_ok=True)
    raise BundleError(f"Unable to download {filename}")


def merge_tree(source, destination, prefix_root, visited):
    for entry in os.scandir(source):
        target = destination / entry.name
        if entry.is_symlink():
            link = os.readlink(entry.path)
            resolved = Path(link)
            if not resolved.is_absolute():
                resolved = (Path(entry.path).parent / resolved)
            resolved = Path(os.path.normpath(resolved))
            if str(resolved).startswith(TERMUX_PREFIX):
                resolved = prefix_root / resolved.relative_to(TERMUX_PREFIX)
            elif not str(resolved).startswith(str(prefix_root)):
                continue
            if resolved.is_file():
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(resolved, target)
            elif resolved.is_dir():
                real = resolved.resolve()
                if real in visited:
                    continue
                visited.add(real)
                target.mkdir(parents=True, exist_ok=True)
                merge_tree(resolved, target, prefix_root, visited)
        elif entry.is_dir(follow_symlinks=False):
            target.mkdir(parents=True, exist_ok=True)
            merge_tree(Path(entry.path), target, prefix_root, visited)
        elif entry.is_file(follow_symlinks=False):
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(entry.path, target)


def is_aarch64_elf(path):
    try:
        with open(path, "rb") as source:
            header = source.read(20)
    except OSError:
        return False
    return (
        len(header) == 20
        and header[:4] == b"\x7fELF"
        and header[4] == 2
        and header[5] == 1
        and int.from_bytes(header[18:20], "little") == 183
    )


def patch_rpaths(root):
    for path in root.rglob("*"):
        if not path.is_file() or not is_aarch64_elf(path):
            continue
        relative = os.path.relpath(root / "lib", path.parent)
        rpath = "$ORIGIN" if relative == "." else f"$ORIGIN/{relative}"
        lib64 = root / "lib64"
        if lib64.is_dir():
            relative64 = os.path.relpath(lib64, path.parent)
            rpath += f":$ORIGIN/{relative64}"
        subprocess.run(
            ["patchelf", "--set-rpath", rpath, str(path)],
            check=True,
            stdout=subprocess.DEVNULL,
        )


def sanitize_version(value):
    if ":" in value:
        value = value.split(":", 1)[1]
    return re.sub(r"[^A-Za-z0-9._+-]", "_", value)


def entrypoint_path(root, candidates, bundle_id, key):
    for relative in candidates:
        candidate = root / relative
        if candidate.is_file():
            return relative
    raise BundleError(f"Bundle {bundle_id} is missing entrypoint {key}: {candidates}")


def build_bundle(spec, packages, cache, output):
    log(f"Building {spec['id']}")
    resolved = resolve_packages(spec["packages"], packages, provides)
    log(f"  {len(resolved)} package(s): {', '.join(sorted(resolved))}")

    with tempfile.TemporaryDirectory(prefix=f"ps-{spec['id']}-") as temporary:
        temporary = Path(temporary)
        root = temporary / "bundle"
        root.mkdir()
        visited = set()
        for name, index in resolved.items():
            archive = cache / f"{name}_{sanitize_version(index['Version'])}.deb"
            if not archive.is_file():
                download_package(index, archive)
            extract = temporary / f"extract-{name}"
            extract.mkdir()
            subprocess.run(
                ["dpkg-deb", "-x", str(archive), str(extract)],
                check=True,
                stdout=subprocess.DEVNULL,
            )
            prefix_root = extract / "data" / "data" / "com.termux" / "files" / "usr"
            if not prefix_root.is_dir():
                raise BundleError(f"{name} does not contain the Termux prefix")
            merge_tree(prefix_root, root, prefix_root, visited)

        patch_rpaths(root)

        entrypoints = {}
        for key, candidates in spec["entrypoints"].items():
            relative = entrypoint_path(root, candidates, spec["id"], key)
            if not is_aarch64_elf(root / relative):
                raise BundleError(f"{spec['id']} entrypoint {relative} is not an AArch64 ELF")
            entrypoints[key] = relative

        version = sanitize_version(resolved[spec["packages"][0]]["Version"])
        manifest = {
            "formatVersion": 1,
            "id": spec["id"],
            "version": version,
            "abi": "arm64-v8a",
            "description": spec["description"],
            "license": "Open-source packages from the Termux repository; see share/doc in the bundle",
            "entrypoints": entrypoints,
        }
        (root / "manifest.json").write_text(
            json.dumps(manifest, indent=2) + "\n", encoding="utf-8"
        )

        filename = f"{spec['id']}-{version}-arm64.zip"
        archive = output / filename
        with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as zip_file:
            for path in sorted(root.rglob("*")):
                if not path.is_file():
                    continue
                relative = path.relative_to(root).as_posix()
                info = zipfile.ZipInfo(relative)
                info.compress_type = zipfile.ZIP_DEFLATED
                info.external_attr = (stat.S_IMODE(path.stat().st_mode) | stat.S_IFREG) << 16
                with open(path, "rb") as source, zip_file.open(info, "w") as target:
                    shutil.copyfileobj(source, target)

        digest = sha256_file(archive)
        log(f"  wrote {filename} ({archive.stat().st_size} bytes, sha256 {digest})")
        return {
            "id": spec["id"],
            "version": version,
            "filename": filename,
            "sha256": digest,
            "sizeBytes": archive.stat().st_size,
            "description": f"{spec['description']} ({version})",
        }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default="dist/runtime-bundles")
    parser.add_argument("--release-tag", default="runtime-bundles-v1")
    parser.add_argument("--repo", default=os.environ.get("GITHUB_REPOSITORY", "emonbudc/PocketServer"))
    parser.add_argument("--only", default="", help="Comma-separated bundle ids to build")
    arguments = parser.parse_args()

    output = Path(arguments.out)
    output.mkdir(parents=True, exist_ok=True)
    cache = Path(".cache/termux-packages")
    cache.mkdir(parents=True, exist_ok=True)

    global provides
    index_text = None
    for mirror in MIRRORS:
        try:
            log(f"Fetching {mirror}{INDEX_PATH}")
            index_text = request(mirror + INDEX_PATH).decode("utf-8", errors="replace")
            break
        except Exception as exception:
            log(f"Index mirror failed: {exception}")
    if index_text is None:
        raise BundleError("Unable to fetch the Termux package index")
    packages, provides = parse_packages(index_text)

    release_base = (
        f"https://github.com/{arguments.repo}/releases/download/"
        f"{urllib.parse.quote(arguments.release_tag, safe='')}/"
    )
    wanted = {item.strip() for item in arguments.only.split(",") if item.strip()}
    bundles = []
    for spec in BUNDLES:
        if wanted and spec["id"] not in wanted:
            continue
        result = build_bundle(spec, packages, cache, output)
        result["url"] = release_base + urllib.parse.quote(result["filename"])
        bundles.append(result)

    catalog = {"formatVersion": 1, "bundles": bundles}
    catalog_path = output / "runtime-catalog.json"
    catalog_path.write_text(json.dumps(catalog, indent=2) + "\n", encoding="utf-8")
    checksums = "".join(
        f"{bundle['sha256']}  {bundle['filename']}\n" for bundle in bundles
    )
    (output / "runtime-bundles.sha256").write_text(checksums, encoding="utf-8")
    log(f"Wrote {catalog_path}")
    log(json.dumps(catalog, indent=2))


if __name__ == "__main__":
    try:
        main()
    except Exception as exception:
        print(f"error: {exception}", file=sys.stderr)
        sys.exit(1)
