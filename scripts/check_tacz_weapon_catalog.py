#!/usr/bin/env python3
"""Check 14 weapon IDs and every sight/suppressor pairing against REAL TaCZ.

Default mode fetches the unmodified pinned 1.21.1 NeoForge r7 JAR from
Modrinth, validates the SHA-512, and follows the exact TaCZ default-pack
attachment tag graph. --jar may be used to check a local vendor JAR instead.

The policy JSON contains IDs and per-gun installed accessory restrictions.
TaCZ assets are only READ for verification; never committed or redistributed.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import urllib.parse
import urllib.request
import zipfile

PACK_PREFIX = "assets/tacz/custom/tacz_default_gun/data/tacz/"
POLICY = (Path(__file__).resolve().parents[1]
          / "src/main/resources/data/zombieapocalypse/tacz_weapon_allowlist.json")
VERSION_ID = "62q6mB5Q"
PROJECT_ID = "OypNE65K"
VERSION_URL = f"https://api.modrinth.com/v2/version/{VERSION_ID}"


def download_current_pinned_jar(out: Path) -> Path:
    headers = {"User-Agent": "ZombieApocalypse-Whitelist-Validator/1.0"}
    with urllib.request.urlopen(
        urllib.request.Request(VERSION_URL, headers=headers), timeout=40
    ) as stream:
        version = json.load(stream)
    if (version.get("id"), version.get("project_id")) != (VERSION_ID, PROJECT_ID):
        raise ValueError("Unexpected Modrinth project/version")
    if "1.21.1" not in version.get("game_versions", []):
        raise ValueError("TaCZ version is not compatible with Minecraft 1.21.1")
    if "neoforge" not in version.get("loaders", []):
        raise ValueError("TaCZ version is not a NeoForge build")
    files = [f for f in version.get("files", [])
             if f.get("filename", "").endswith(".jar")
             and not f.get("filename", "").endswith("-sources.jar")
             and not f.get("filename", "").endswith("-dev.jar")]
    if not files:
        raise ValueError("TaCZ pinned version has no runtime JAR")
    entry = next((f for f in files if f.get("primary")), files[0])
    url = entry.get("url", "")
    if (urllib.parse.urlparse(url).scheme != "https"
            or urllib.parse.urlparse(url).hostname != "cdn.modrinth.com"):
        raise ValueError("TaCZ artifact is not from official Modrinth CDN")
    sha512 = entry.get("hashes", {}).get("sha512", "")
    if not re.fullmatch("[0-9a-fA-F]{128}", sha512):
        raise ValueError("Missing SHA-512 for original TaCZ JAR")

    request = urllib.request.Request(url, headers=headers)
    hasher = hashlib.sha512()
    with urllib.request.urlopen(request, timeout=90) as response, out.open("wb") as dst:
        while chunk := response.read(1024 * 1024):
            hasher.update(chunk)
            dst.write(chunk)
    if hasher.hexdigest() != sha512.lower():
        out.unlink(missing_ok=True)
        raise ValueError("Downloaded TaCZ runtime JAR failed SHA-512 verification")
    return out


def parse_tacz_jsonc(raw: str):
    """TaCZ default-pack metadata accepts // comments and trailing commas.

    Strip those without altering JSON string literals (including URLs).
    This validator never executes JavaScript or vendor assets.
    """
    result = []
    i = 0
    in_string = False
    escaped = False
    while i < len(raw):
        ch = raw[i]
        next_char = raw[i + 1] if i + 1 < len(raw) else ""
        if in_string:
            result.append(ch)
            if escaped:
                escaped = False
            elif ch == "\\":
                escaped = True
            elif ch == '"':
                in_string = False
            i += 1
            continue
        if ch == '"':
            in_string = True
            result.append(ch)
            i += 1
            continue
        if ch == "/" and next_char == "/":
            i = raw.find("\n", i)
            if i == -1:
                break
            result.append("\n")
            i += 1
            continue
        if ch == "/" and next_char == "*":
            end = raw.find("*/", i + 2)
            if end == -1:
                raise ValueError("Unterminated block comment in TaCZ metadata")
            result.append("\n" * raw[i:end + 2].count("\n"))
            i = end + 2
            continue
        # The stock TaCZ JSONC uses trailing commas; detect only those
        # outside quoted strings, with comments already stripped above.
        result.append(ch)
        i += 1
    cleaned = "".join(result)
    # Remove commas outside JSON strings immediately preceding closing } or ].
    output = []
    in_string = escaped = False
    i = 0
    while i < len(cleaned):
        ch = cleaned[i]
        if in_string:
            output.append(ch)
            if escaped:
                escaped = False
            elif ch == "\\":
                escaped = True
            elif ch == '"':
                in_string = False
        elif ch == '"':
            in_string = True
            output.append(ch)
        elif ch == "," and re.match(r"\s*[}\]]", cleaned[i + 1:]):
            pass
        else:
            output.append(ch)
        i += 1
    return json.loads("".join(output))


def parse_json_from_jar(jar: zipfile.ZipFile, relative_path: str):
    path = PACK_PREFIX + relative_path
    return parse_tacz_jsonc(jar.read(path).decode("utf-8-sig"))


def expand_tags(jar: zipfile.ZipFile, group: str) -> set[str]:
    """Use TaCZ's own recursively resolved #tacz:* attachment tag semantics."""
    root = parse_json_from_jar(
        jar, f"tacz_tags/attachments/allow_attachments/{group}.json")
    visited: set[str] = set()
    result: set[str] = set()

    def walk(items):
        for item in items:
            if item.startswith("#tacz:"):
                tag = item.removeprefix("#tacz:")
                if tag in visited:
                    continue
                visited.add(tag)
                walk(parse_json_from_jar(jar, f"tacz_tags/attachments/{tag}.json"))
            else:
                result.add(item)

    walk(root)
    return result


def validate(jar: zipfile.ZipFile, policy: dict) -> list[str]:
    assert policy.get("format_version") == 1, "Unexpected policy version"
    guns = policy.get("guns")
    assert isinstance(guns, list) and len(guns) == 14, "Must select 14 guns"
    seen: set[str] = set()
    used_attachments: set[str] = set()
    lines = []

    for row in guns:
        gun = row["id"]
        assert re.fullmatch(r"tacz:[a-z0-9_]+", gun), f"Invalid ID: {gun}"
        assert gun not in seen, f"Duplicate gun: {gun}"
        seen.add(gun)
        name = gun.removeprefix("tacz:")
        gun_index = parse_json_from_jar(jar, f"index/guns/{name}.json")
        assert gun_index.get("data") == f"tacz:{name}_data", (
            f"Unexpected data pointer for {gun}")

        allowed = expand_tags(jar, name)
        for slot, required_type in (("scope", "scope"), ("muzzle", "muzzle")):
            attachment = row.get(slot)
            if attachment is None:
                continue
            assert re.fullmatch(r"tacz:[a-z0-9_]+", attachment), attachment
            attachment_name = attachment.removeprefix("tacz:")
            index = parse_json_from_jar(
                jar, f"index/attachments/{attachment_name}.json")
            assert index.get("type") == required_type, (
                f"{gun}: {attachment} is not of type {required_type}")
            assert attachment in allowed, (
                f"{gun}: {attachment} is NOT permitted by TaCZ's original tags")
            assert not index.get("hidden", False), (
                f"{gun}: hidden built-in optic cannot be distributed as loose loot")
            used_attachments.add(attachment)

        lines.append(
            f"{gun:18} optic={str(row.get('scope')):31} muzzle={row.get('muzzle')}")
    assert len(seen) == 14
    assert len(used_attachments) >= 10, (
        "Attachment pool unexpectedly small; validate selection")
    # AUG uses an invisible built-in scope, which must NOT be distributed as loot.
    assert next(x for x in guns if x["id"] == "tacz:aug")["scope"] is None
    return lines


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", type=Path,
                        help="Use provided local TaCZ JAR; otherwise download pinned r7")
    parser.add_argument("--policy", type=Path, default=POLICY)
    args = parser.parse_args()
    policy = json.loads(args.policy.read_text(encoding="utf-8"))
    if args.jar is None:
        import tempfile
        with tempfile.TemporaryDirectory() as temp:
            jar_path = download_current_pinned_jar(Path(temp) / "tacz-original.jar")
            with zipfile.ZipFile(jar_path) as archive:
                lines = validate(archive, policy)
    else:
        with zipfile.ZipFile(args.jar) as archive:
            lines = validate(archive, policy)
    print("PASS: all 14 guns exist and each accessory matches real TaCZ allowed tags")
    print("\n".join(lines))


if __name__ == "__main__":
    main()
