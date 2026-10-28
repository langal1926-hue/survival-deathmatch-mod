#!/usr/bin/env python3
"""Offline regression tests for one-file Zombie Apocalypse SMP .mrpack builder."""
from __future__ import annotations
import json
from pathlib import Path
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parent))
import build_modpack as pack


def fake_version():
    return {
        "id": pack.TACZ_VERSION,
        "project_id": pack.TACZ_PROJECT,
        "game_versions": ["1.21.1"],
        "loaders": ["neoforge"],
        "files": [
            {
                "filename": "tacz-neoforge-1.21.1-1.1.8-hotfix-r7.jar",
                "primary": True,
                "size": 55_000_000,
                "url": "https://cdn.modrinth.com/data/OypNE65K/versions/62q6mB5Q/tacz-neoforge-1.21.1-1.1.8-hotfix-r7.jar",
                "hashes": {"sha1": "a" * 40, "sha512": "b" * 128},
            }
        ],
    }


class TestModpack(unittest.TestCase):
    def test_pack_embeds_our_jar_and_references_original_tacz(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            libs = root / "build/libs"
            libs.mkdir(parents=True)
            jar = libs / "zombieapocalypse-0.2.0.jar"
            with zipfile.ZipFile(jar, "w") as out:
                out.writestr("META-INF/neoforge.mods.toml", 'modId="zombieapocalypse"')
            self.assertEqual(pack.resolve_mod_jar(libs), jar)

            result = root / "dist/Zombie-Apocalypse-SMP.mrpack"
            index = pack.create_pack(jar, result, fake_version(), "deadbeef123456")
            self.assertEqual(index["dependencies"], {
                "minecraft": "1.21.1", "neoforge": "21.1.264"})
            self.assertEqual(index["files"][0]["env"], {
                "client": "required", "server": "required"})
            self.assertEqual(len(index["files"]), 1)
            self.assertEqual(index["files"][0]["path"],
                "mods/tacz-neoforge-1.21.1-1.1.8-hotfix-r7.jar")
            with zipfile.ZipFile(result) as archive:
                self.assertEqual(set(archive.namelist()), {
                    "modrinth.index.json",
                    "overrides/mods/zombieapocalypse-0.2.0.jar",
                    "overrides/README-ZA-SMP.txt",
                })
                parsed = json.loads(archive.read("modrinth.index.json"))
                self.assertEqual(parsed, index)
                self.assertNotIn(
                    b"tacz-neoforge", archive.read(
                        "overrides/mods/zombieapocalypse-0.2.0.jar"))
                self.assertIn("https://cdn.modrinth.com", str(parsed["files"]))
                self.assertEqual(zipfile.ZipFile(result).testzip(), None)

    def test_reject_fake_version_or_external_urls(self):
        wrong_version = fake_version()
        wrong_version["id"] = "incorrect"
        with self.assertRaises(ValueError):
            pack.select_tacz_file(wrong_version)

        wrong_project = fake_version()
        wrong_project["project_id"] = "unrelated"
        with self.assertRaises(ValueError):
            pack.select_tacz_file(wrong_project)

        unsafe_url = fake_version()
        unsafe_url["files"][0]["url"] = "https://evil.example/file.jar"
        with self.assertRaises(ValueError):
            pack.select_tacz_file(unsafe_url)

        unsafe_name = fake_version()
        unsafe_name["files"][0]["filename"] = "../../something.jar"
        with self.assertRaises(ValueError):
            pack.select_tacz_file(unsafe_name)

        source_only = fake_version()
        source_only["files"][0]["filename"] = "tacz-sources.jar"
        with self.assertRaises(ValueError):
            pack.select_tacz_file(source_only)

    def test_never_select_javadoc_or_dev_jar(self):
        with tempfile.TemporaryDirectory() as folder:
            p = Path(folder)
            for name in ["zombieapocalypse-0.2.0-sources.jar",
                         "zombieapocalypse-0.2.0-javadoc.jar"]:
                with zipfile.ZipFile(p / name, "w"):
                    pass
            with self.assertRaises(ValueError):
                pack.resolve_mod_jar(p)


if __name__ == "__main__":
    unittest.main()
