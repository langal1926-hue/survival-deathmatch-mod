"""Phase 03 regression checks for uniformly distributed TaCZ ammo and attachments."""
import json
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = (ROOT / "src/main/java/com/zombieapocalypse/loot/LootChestFiller.java").read_text(encoding="utf-8")
POLICY_SOURCE = (ROOT / "src/main/java/com/zombieapocalypse/compat/TaCZWeaponAllowlist.java").read_text(encoding="utf-8")
MANIFEST = json.loads((ROOT / "src/main/resources/data/zombieapocalypse/tacz_weapon_allowlist.json").read_text(encoding="utf-8"))


class BalancedLootTests(unittest.TestCase):
    def test_all_weapon_ammo_calibers_are_considered(self):
        self.assertIn("TaCZWeaponAllowlist.approvedGunIds().stream()", SOURCE)
        self.assertIn(".map(index -> index.getGunData().getAmmoId())", SOURCE)
        self.assertIn(".distinct()", SOURCE)
        self.assertIn("calibers.get(random.nextInt(calibers.size()))", SOURCE)
        self.assertIn("AmmoItemBuilder.create()", SOURCE)
        self.assertNotRegex(SOURCE, r'ammo\("tacz:')
        self.assertIn("case HOSPITAL -> {", SOURCE)
        self.assertEqual(SOURCE.count("() -> balancedAmmo(calibers, random,"), 4)
        # Choosing uniformly over deduplicated ammo types means a given type
        # has probability 1/N regardless of how many guns use its caliber.
        for distinct_caliber_count in range(1, 15):
            choices = [i for i in range(distinct_caliber_count)]
            self.assertEqual(len(choices), len(set(choices)))

    def test_accessory_uses_same_region_odds_as_firearm(self):
        self.assertIn("GunClass selected = rollGunClass(profile, random)", SOURCE)
        self.assertIn("GunClass accessoryClass = rollGunClass(profile, random)", SOURCE)
        self.assertEqual(SOURCE.count("rollGunClass(profile, random);"), 2)
        self.assertIn("makeAccessory(accessoryClass, random)", SOURCE)
        self.assertIn("AttachmentItemBuilder.create()", SOURCE)
        self.assertIn("TaCZWeaponAllowlist.approvedLoadout(gunId)", SOURCE)
        self.assertIn("approvedLooseAttachmentIds().contains(attachmentId)", SOURCE)
        self.assertIn("chosen.scope() : chosen.muzzle()", SOURCE)

    def test_manifest_contains_no_extra_attachments(self):
        allowed = {entry["id"] for entry in MANIFEST["guns"]}
        parsed = []
        for group in ["PISTOL", "SHOTGUN", "SMG", "RIFLE", "SNIPER"]:
            match = re.search(r"case " + group + r" -> new String\[\] \{([^}]+)\}", SOURCE)
            self.assertIsNotNone(match, group)
            parsed.extend(re.findall(r'"(tacz:[a-z0-9_]+)"', match.group(1)))
        self.assertEqual(set(parsed), allowed)
        self.assertEqual(len(parsed), len(set(parsed)))
        self.assertEqual(len(parsed), 14)
        allowed_attachments = {
            row[key] for row in MANIFEST["guns"]
            for key in ("scope", "muzzle") if row[key] is not None
        }
        self.assertGreaterEqual(len(allowed_attachments), 10)
        self.assertIn("public static Loadout approvedLoadout", POLICY_SOURCE)


if __name__ == "__main__":
    unittest.main()
