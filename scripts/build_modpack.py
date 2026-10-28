#!/usr/bin/env python3
"""Build a single importable Zombie Apocalypse SMP Modrinth modpack (.mrpack).

Our compiled zombieapocalypse mod JAR is embedded as a private-pack override.
The official Modrinth CDN supplies the *unchanged* TaCZ NeoForge mod on first
install. This does not repackage TaCZ's proprietary models/sounds into a
redistributed JAR, and players do not have to download TaCZ separately.

Requires Python 3.10+ (standard library only). Intended for GitHub Actions
after Gradle's 'build' task.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path
import urllib.parse
import urllib.request
import zipfile

MINECRAFT = "1.21.1"
NEOFORGE = "21.1.264"
TACZ_PROJECT = "OypNE65K"
TACZ_VERSION = "62q6mB5Q"
TACZ_API = f"https://api.modrinth.com/v2/version/{TACZ_VERSION}"
PACK_NAME = "Zombie Apocalypse SMP"

README = """Zombie Apocalypse SMP - Minecraft 1.21.1 / NeoForge 21.1.264

This profile contains:
- Zombie Apocalypse SMP (the included build from this project's GitHub CI)
- TaCZ 1.21.1 NeoForge 1.1.8-hotfix-r7 (unmodified, downloaded by the launcher)

Install: Import the .mrpack into Modrinth App or Prism Launcher.
The launcher will automatically install NeoForge and download TaCZ.
There is no need to install the old G17 asset ZIP or a separate TaCZ JAR.

All server participants must run the same versions of both mods.
This is an early test build; the 14-gun whitelist, city loot containers,
60-minute restock, mining ban, merchants and economy are not complete yet.

TaCZ: https://modrinth.com/mod/tacz-1.21.1/version/62q6mB5Q
Project: https://github.com/langal1926-hue/survival-deathmatch-mod

Important: This modpack file contains the Zombie Apocalypse SMP JAR but does
not bundle the TaCZ third-party binary. The launcher downloads the original
TaCZ mod from its publisher's Modrinth CDN when the profile is installed.
"""


def fetch_version_metadata() -> dict:
    request = urllib.request.Request(
        TACZ_API,
        headers={"User-Agent": "ZombieApocalypseSMP-PackBuilder/1.0",
                 "Accept": "application/json"},
    )
    with urllib.request.urlopen(request, timeout=35) as response:
        return json.load(response)


def select_tacz_file(metadata: dict) -> dict:
    """Validate pinned publisher metadata rather than blindly trusting URLs."""
    if metadata.get("id") != TACZ_VERSION or metadata.get("project_id") != TACZ_PROJECT:
        raise ValueError("Unexpected TaCZ Modrinth project/version ID")
    if MINECRAFT not in metadata.get("game_versions", []):
        raise ValueError("Pinned TaCZ version is not marked for Minecraft 1.21.1")
    if "neoforge" not in metadata.get("loaders", []):
        raise ValueError("Pinned TaCZ version is not marked NeoForge")
    files = [f for f in metadata.get("files", [])
             if f.get("filename", "").endswith(".jar")
             and not f.get("filename", "").endswith("-sources.jar")
             and not f.get("filename", "").endswith("-dev.jar")]
    if not files:
        raise ValueError("Pinned TaCZ version has no installable JAR")
    file = next((f for f in files if f.get("primary")), files[0])
    filename = file["filename"]
    if not re.fullmatch(r"[A-Za-z0-9_.+-]+\.jar", filename):
        raise ValueError("Unsafe TaCZ file name")
    url = file.get("url", "")
    parsed = urllib.parse.urlsplit(url)
    if parsed.scheme != "https" or parsed.hostname != "cdn.modrinth.com":
        raise ValueError("TaCZ download must be an official Modrinth CDN HTTPS URL")
    hashes = file.get("hashes", {})
    sha1 = hashes.get("sha1", "")
    sha512 = hashes.get("sha512", "")
    if not re.fullmatch(r"[a-fA-F0-9]{40}", sha1):
        raise ValueError("TaCZ SHA-1 missing or invalid")
    if not re.fullmatch(r"[a-fA-F0-9]{128}", sha512):
        raise ValueError("TaCZ SHA-512 missing or invalid")
    size = file.get("size", 0)
    if not isinstance(size, int) or size < 1_000_000:
        raise ValueError("TaCZ JAR file size is invalid")
    return {
        "path": f"mods/{filename}",
        "hashes": {"sha1": sha1.lower(), "sha512": sha512.lower()},
        "env": {"client": "required", "server": "required"},
        "downloads": [url],
        "fileSize": size,
    }


def resolve_mod_jar(jar_dir: Path) -> Path:
    candidates = sorted(
        jar for jar in jar_dir.glob("zombieapocalypse-*.jar")
        if not any(jar.name.endswith(suffix) for suffix in
                   ("-sources.jar", "-javadoc.jar", "-dev.jar", "-all.jar"))
    )
    if len(candidates) != 1:
        raise ValueError(
            f"Expected exactly one compiled Zombie Apocalypse release JAR in "
            f"{jar_dir}; found {[x.name for x in candidates]}")
    with zipfile.ZipFile(candidates[0]) as archive:
        names = set(archive.namelist())
        if "META-INF/neoforge.mods.toml" not in names:
            raise ValueError("Compiled zombie mod JAR lacks NeoForge mod metadata")
        toml = archive.read("META-INF/neoforge.mods.toml").decode("utf-8")
        if 'modId="zombieapocalypse"' not in toml:
            raise ValueError("Wrong mod JAR: zombieapocalypse mod ID not found")
    return candidates[0]


def create_pack(jar: Path, target: Path, version_metadata: dict, commit: str) -> dict:
    """Package exact built JAR and pinned TaCZ CDN reference; no third-party JAR."""
    jar = Path(jar)
    target = Path(target)
    if not jar.is_file() or not zipfile.is_zipfile(jar):
        raise ValueError("The compiled zombie mod JAR is missing or invalid")
    tacz = select_tacz_file(version_metadata)

    version = commit[:12].lower()
    if not re.fullmatch(r"[0-9a-f]{7,12}", version):
        raise ValueError("Commit must be a 7-12 character Git commit prefix")

    index = {
        "formatVersion": 1,
        "game": "minecraft",
        "versionId": f"0.2.0-{version}",
        "name": PACK_NAME,
        "summary": "Zombie apocalypse PvPvE survival with full TaCZ gun engine.",
        "files": [tacz],
        "dependencies": {
            "minecraft": MINECRAFT,
            "neoforge": NEOFORGE,
        },
    }
    target.parent.mkdir(parents=True, exist_ok=True)

    # Do not expose complete TaCZ assets/JAR in this pack. A .mrpack launcher
    # downloads the publisher's unchanged release when the user imports it.
    with zipfile.ZipFile(target, "w", compression=zipfile.ZIP_DEFLATED) as output:
        output.writestr("modrinth.index.json",
                        json.dumps(index, indent=2, ensure_ascii=False) + "\n")
        output.write(jar, arcname=f"overrides/mods/{jar.name}")
        output.writestr("overrides/README-ZA-SMP.txt", README)

    with zipfile.ZipFile(target) as output:
        if output.testzip() is not None:
            raise ValueError("Modpack output is corrupted")
        assert len(output.namelist()) == 3

    print(f"Created {target} ({target.stat().st_size:,} bytes)")
    print(f"Included our zombie mod: {jar.name}")
    print(f"TaCZ installed automatically by launcher: {tacz['path']}")
    return index


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar-dir", default="build/libs")
    parser.add_argument("--output", default="dist/Zombie-Apocalypse-SMP.mrpack")
    parser.add_argument("--commit", required=True,
                        help="Current Git revision (from GITHUB_SHA)")
    args = parser.parse_args()
    jar = resolve_mod_jar(Path(args.jar_dir))
    create_pack(jar, Path(args.output), fetch_version_metadata(), args.commit)


if __name__ == "__main__":
    main()
