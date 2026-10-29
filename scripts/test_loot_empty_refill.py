"""Regression guard for empty-only 60-minute chest restock rules.

This protects the persisted state transitions against accidental reintroduction
of the former timed-while-stocked refill behavior. Actual Minecraft runtime
behavior is additionally verified by the Gradle build and manual playtesting.
"""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DATA = (ROOT / "src/main/java/com/zombieapocalypse/loot/LootChestData.java").read_text(encoding="utf-8")
COMMANDS = (ROOT / "src/main/java/com/zombieapocalypse/command/LootChestCommands.java").read_text(encoding="utf-8")


class EmptyOnlyRefillTests(unittest.TestCase):
    def test_registered_chest_starts_with_no_countdown(self):
        self.assertIn("chests.put(pos.asLong(), new Entry(profile, 0L));", DATA)
        self.assertIn("if (inserted <= 0) return -1;", DATA)
        self.assertIn("public boolean waitingForEmpty()", DATA)

    def test_only_empty_chests_start_the_sixty_minute_timer(self):
        body = DATA.split("private static Entry advance(", 1)[1].split("public void tick(", 1)[0]
        self.assertIn("if (!chest.isEmpty())", body)
        self.assertIn("new Entry(entry.profile(), 0L)", body)
        self.assertIn("if (entry.waitingForEmpty())", body)
        self.assertIn("new Entry(entry.profile(), now + REFILL_MILLIS)", body)
        self.assertIn("if (now < entry.refillAt())", body)
        self.assertLess(body.index("if (!chest.isEmpty())"), body.index("if (entry.waitingForEmpty())"))
        self.assertLess(body.index("if (entry.waitingForEmpty())"), body.index("if (now < entry.refillAt())"))
        self.assertLess(body.index("if (now < entry.refillAt())"), body.index("LootChestFiller.fill(level, chest, entry.profile())"))
        self.assertIn("inserted > 0 ? 0L : now + REFILL_MILLIS", body)

    def test_operator_refill_requires_empty_chest(self):
        section = DATA.split("public int refillNow(", 1)[1].split("public Entry observe(", 1)[0]
        self.assertIn("if (!chest.isEmpty()) return -2;", section)
        self.assertIn("new Entry(entry.profile(), 0L)", section)
        self.assertIn("filled == -2", COMMANDS)
        self.assertIn("entry.waitingForEmpty()", COMMANDS)

    def test_existing_chest_registrations_migrate_without_old_timers(self):
        self.assertIn("private static final int DATA_VERSION = 2;", DATA)
        self.assertIn('boolean legacy = root.getInt("formatVersion") != DATA_VERSION;', DATA)
        self.assertIn('long deadline = legacy ? 0L : Math.max(0L, tag.getLong("refillAt"));', DATA)
        self.assertIn('root.putInt("formatVersion", DATA_VERSION);', DATA)
        self.assertIn("if (legacy) data.setDirty();", DATA)
        self.assertIn("if (!level.hasChunkAt(pos)) continue;", DATA)
        self.assertIn("if (!eligible(level, pos))", DATA)

    def test_wall_clock_duration_remains_sixty_minutes(self):
        self.assertIn("REFILL_MILLIS = 60L * 60L * 1000L", DATA)
        self.assertIn("new Entry(entry.profile(), now + REFILL_MILLIS)", DATA)


if __name__ == "__main__":
    unittest.main()
