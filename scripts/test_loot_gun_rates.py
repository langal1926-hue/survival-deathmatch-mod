"""Regression checks for fixed per-chest firearm drop probabilities.

The Java implementation draws exactly one integer in [0, 1000), and the
thresholds are mutually exclusive. These checks ensure the integer weights,
allowlisted gun IDs and supply-only pools do not drift during later edits.
"""
import json
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FILLER = (ROOT / "src/main/java/com/zombieapocalypse/loot/LootChestFiller.java").read_text(encoding="utf-8")
MANIFEST = json.loads((ROOT / "src/main/resources/data/zombieapocalypse/tacz_weapon_allowlist.json").read_text(encoding="utf-8"))

EXPECTED = {
    "CIVILIAN, HOSPITAL": (30, 20, 10, 0, 3),
    "POLICE": (200, 150, 100, 0, 5),
    "MILITARY": (70, 100, 100, 270, 100),
}
GROUPS = ("PISTOL", "SHOTGUN", "SMG", "RIFLE", "SNIPER")


class GunLootTests(unittest.TestCase):
    def test_exact_rates_and_single_roll(self):
        self.assertIn("random.nextInt(1000)", FILLER)
        self.assertIn("GunClass selected = rollGunClass(profile, random)", FILLER)
        for key, expected in EXPECTED.items():
            pattern = r"case " + re.escape(key) + r" -> new GunOdds\(([^)]+)\)"
            match = re.search(pattern, FILLER)
            self.assertIsNotNone(match, f"Missing odds for {key}")
            weights = tuple(int(x.strip()) for x in match.group(1).split(","))
            self.assertEqual(weights, expected)
            self.assertLessEqual(sum(weights), 1000)
            # Enumerate every possible roll to prove that all exact per-mille
            # category probabilities and the remainder have no rounding error.
            outcomes = [0] * 6
            for roll in range(1000):
                remaining = roll
                for index, probability in enumerate(weights):
                    if remaining < probability:
                        outcomes[index] += 1
                        break
                    remaining -= probability
                else:
                    outcomes[5] += 1
            self.assertEqual(outcomes, list(weights) + [1000 - sum(weights)])

    def test_weapon_groups_cover_exactly_the_allowed_14_guns(self):
        found = []
        for group in GROUPS:
            m = re.search(r"case " + group + r" -> new String\[\] \{([^}]+)\}", FILLER)
            self.assertIsNotNone(m, f"No ID list for {group}")
            found.extend(re.findall(r'"(tacz:[a-z0-9_]+)"', m.group(1)))
        allowed = {entry["id"] for entry in MANIFEST["guns"]}
        self.assertEqual(len(found), len(set(found)), "Duplicate gun appears in multiple categories")
        self.assertEqual(set(found), allowed)

    def test_generic_supply_pool_cannot_spawn_additional_guns(self):
        supply_section = FILLER.split("private static List<Drop> supplyPool(")[1].split("private static List<ResourceLocation> eligibleAmmoIds(")[0]
        self.assertNotIn("gun(drops", supply_section)
        self.assertNotIn("makeGun(", supply_section)
        self.assertIn("if (emptySlots.isEmpty()) return 0", FILLER)
        self.assertIn("chest.getItem(slot).isEmpty()", FILLER)


if __name__ == "__main__":
    unittest.main()
