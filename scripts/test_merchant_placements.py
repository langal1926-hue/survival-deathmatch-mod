"""Regression checks for Phase 02 merchant chest and bed exceptions."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "src/main/java/com/zombieapocalypse"
MATERIALS = (ROOT / "survival/ConstructionMaterials.java").read_text(encoding="utf-8")
RULES = (ROOT / "survival/SurvivalRuleEvents.java").read_text(encoding="utf-8")
LEDGER = (ROOT / "survival/ConstructionLedger.java").read_text(encoding="utf-8")
LOOT = (ROOT / "loot/LootChestData.java").read_text(encoding="utf-8")


class MerchantPlacementTests(unittest.TestCase):
    def test_chest_alias_and_vanilla_chest(self):
        self.assertIn("Blocks.CHEST, ModItems.CONSTRUCTION_CHEST", MATERIALS)
        self.assertIn("placed == Blocks.CHEST && stack.is(Items.CHEST)", MATERIALS)

    def test_any_matching_color_bed_is_allowed(self):
        self.assertIn("placed instanceof BedBlock", MATERIALS)
        self.assertIn("stack.getItem() instanceof BedItem bed", MATERIALS)
        self.assertIn("bed.getBlock() == placed", MATERIALS)

    def test_double_block_bed_ownership_is_persistent(self):
        self.assertIn("event instanceof BlockEvent.EntityMultiPlaceEvent multi", RULES)
        self.assertIn("multi.getReplacedBlockSnapshots()", RULES)
        self.assertIn("ledger.record(snapshot.getPos(), player.getUUID())", RULES)
        self.assertIn("ledger.forget(other)", RULES)
        self.assertIn("setDirty()", LEDGER)

    def test_map_chests_never_become_private_refill_chests(self):
        self.assertIn("chestWouldJoinUnownedMapChest", RULES)
        self.assertIn("ConstructionLedger.get(level).owner(neighbor)", RULES)
        self.assertIn("ConstructionLedger.get(level).owner(pos) == null", LOOT)

    def test_regular_map_protection_still_applies(self):
        self.assertIn("if (owner == null || !owner.equals(player.getUUID()))", RULES)
        self.assertIn("event.setCanceled(true)", RULES)
        self.assertIn("isManufacturingStation(target)", RULES)


if __name__ == "__main__":
    unittest.main()
