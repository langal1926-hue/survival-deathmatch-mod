"""Regression checks for TaCZ loot filtering before pickup.

Static checks complement the CI Java build and pinned TaCZ compatibility
validator; real chest-generation integration still needs an in-game check.
"""
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src/main/java/com/zombieapocalypse"
DATA = ROOT / "src/main/resources/data"
MODIFIER = (JAVA / "loot/ApprovedTaCZLootModifier.java").read_text(encoding="utf-8")
REGISTRY = (JAVA / "loot/ModLootModifiers.java").read_text(encoding="utf-8")
ENTRY = (JAVA / "ZombieApocalypseMod.java").read_text(encoding="utf-8")
FILLER = (JAVA / "loot/LootChestFiller.java").read_text(encoding="utf-8")
AUDIT = (JAVA / "command/LootChestCommands.java").read_text(encoding="utf-8")
ALLOWLIST = (JAVA / "compat/TaCZWeaponAllowlist.java").read_text(encoding="utf-8")
ORDER = json.loads((DATA / "neoforge/loot_modifiers/global_loot_modifiers.json").read_text(encoding="utf-8"))
JSON = json.loads((DATA / "zombieapocalypse/loot_modifiers/approved_tacz.json").read_text(encoding="utf-8"))


class AllowedGeneratedLootTests(unittest.TestCase):
    def test_global_filter_is_registered_and_data_driven(self):
        self.assertFalse(ORDER["replace"], "Must never replace TaCZ/other mods' own loot modifiers")
        self.assertIn("zombieapocalypse:approved_tacz", ORDER["entries"])
        self.assertEqual(JSON["type"], "zombieapocalypse:approved_tacz")
        self.assertEqual(JSON["conditions"], [])
        self.assertIn("ModLootModifiers.SERIALIZERS.register(modEventBus)", ENTRY)
        self.assertIn("GLOBAL_LOOT_MODIFIER_SERIALIZERS", REGISTRY)
        self.assertIn("ApprovedTaCZLootModifier.CODEC", REGISTRY)

    def test_generated_stacks_are_filtered_before_inventory(self):
        self.assertIn("generatedLoot.removeIf(", MODIFIER)
        self.assertIn("!TaCZWeaponAllowlist.isAllowedInventoryStack(stack)", MODIFIER)
        self.assertIn("return generatedLoot;", MODIFIER)
        self.assertIn("implements", "implements")  # the sanitizer is a LootModifier
        self.assertIn("extends LootModifier", MODIFIER)

    def test_custom_chest_builder_validates_encoded_attachment_id(self):
        self.assertIn("TaCZWeaponAllowlist.approvedLoadout(gunId)", FILLER)
        self.assertIn("approvedLooseAttachmentIds().contains(attachmentId)", FILLER)
        self.assertIn("attachmentId.equals(attached.getAttachmentId(built))", FILLER)
        self.assertIn("TaCZWeaponAllowlist.isAllowedInventoryStack(built)", FILLER)
        self.assertIn("return ItemStack.EMPTY;", FILLER)

    def test_read_only_admin_chest_audit(self):
        self.assertIn('Commands.literal("audit")', AUDIT)
        self.assertIn("getAttachmentId(stack)", AUDIT)
        self.assertIn("TaCZWeaponAllowlist.approvedGunIds()", AUDIT)
        self.assertIn("TaCZWeaponAllowlist.isAllowedInventoryStack(stack)", AUDIT)
        self.assertNotIn("chest.setItem(", AUDIT)
        self.assertNotIn("chest.removeItem(", AUDIT)
        self.assertIn("LOOSE_ATTACHMENTS.contains(attachment.getAttachmentId(stack))", ALLOWLIST)


if __name__ == "__main__":
    unittest.main()
