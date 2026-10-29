package com.zombieapocalypse.loot;

import com.zombieapocalypse.survival.ConstructionLedger;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Operator-designated map chests only; no inventory is automatically enrolled.
 * The 60-minute real-world timer starts only AFTER a chest becomes empty.
 * A stocked chest has refillAt = 0, meaning it is waiting to be looted.
 * Unloaded chunks are never loaded just to replenish supplies.
 */
public final class LootChestData extends SavedData {
    public static final long REFILL_MILLIS = 60L * 60L * 1000L;
    private static final String FILE_NAME = "zombieapocalypse_map_loot";
    private static final int DATA_VERSION = 2; // Previously counted from the last refill.
    private static final Factory<LootChestData> FACTORY =
            new Factory<>(LootChestData::new, LootChestData::load);

    public enum Profile {
        CIVILIAN, HOSPITAL, POLICE, MILITARY;
        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** refillAt == 0 means the chest still has loot / no countdown is active. */
    public record Entry(Profile profile, long refillAt) {
        public boolean waitingForEmpty() { return refillAt == 0L; }
    }

    private final Map<Long, Entry> chests = new HashMap<>();

    private LootChestData() {}

    public static LootChestData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, FILE_NAME);
    }

    public static boolean eligible(ServerLevel level, BlockPos pos) {
        // Requiring an explicit, single vanilla chest avoids ambiguous double-
        // chest inventories and accidental use of modded personal storage.
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof ChestBlock
                && state.getValue(ChestBlock.TYPE) == ChestType.SINGLE
                && level.getBlockEntity(pos) instanceof ChestBlockEntity
                && ConstructionLedger.get(level).owner(pos) == null;
    }

    public Entry lookup(BlockPos pos) {
        return chests.get(pos.asLong());
    }

    public int size() {
        return chests.size();
    }

    public int register(ServerLevel level, BlockPos pos, Profile profile, long now) {
        if (!eligible(level, pos) || chests.containsKey(pos.asLong())) return -1;
        ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(pos);
        if (chest == null || !chest.isEmpty()) return -1;
        int inserted = LootChestFiller.fill(level, chest, profile);
        if (inserted <= 0) return -1;
        // Fresh loot does NOT start the timer. It begins once fully looted.
        chests.put(pos.asLong(), new Entry(profile, 0L));
        setDirty();
        return inserted;
    }

    public boolean unregister(BlockPos pos) {
        if (chests.remove(pos.asLong()) == null) return false;
        setDirty();
        return true;
    }

    /**
     * OP test override: refill immediately, but ONLY an entirely empty chest.
     * This cannot be used to stack loot over a partially looted chest.
     * Return -2 if still stocked, -1 if not a registered eligible map chest.
     */
    public int refillNow(ServerLevel level, BlockPos pos, long now) {
        Entry entry = lookup(pos);
        if (entry == null || !eligible(level, pos)) return -1;
        ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(pos);
        if (chest == null) return -1;
        if (!chest.isEmpty()) return -2;

        int inserted = LootChestFiller.fill(level, chest, entry.profile());
        if (inserted > 0) {
            chests.put(pos.asLong(), new Entry(entry.profile(), 0L));
            setDirty();
        }
        return inserted;
    }

    /**
     * Called by /za_loot status to observe an emptied chest immediately,
     * rather than waiting for the next 100-tick background scan.
     */
    public Entry observe(ServerLevel level, BlockPos pos, long now) {
        Entry entry = lookup(pos);
        if (entry == null || !level.hasChunkAt(pos) || !eligible(level, pos)) {
            return null;
        }
        Entry updated = advance(level, pos, entry, now);
        if (!updated.equals(entry)) {
            chests.put(pos.asLong(), updated);
            setDirty();
        }
        return updated;
    }

    /**
     * Stock present: no timer (or cancel an existing timer, if loot returned).
     * Fully empty for the first time: start a 60-minute countdown.
     * Still empty after the countdown: generate a new batch and return to idle.
     */
    private static Entry advance(ServerLevel level, BlockPos pos, Entry entry, long now) {
        if (!(level.getBlockEntity(pos) instanceof ChestBlockEntity chest)) return entry;

        if (!chest.isEmpty()) {
            return entry.waitingForEmpty() ? entry : new Entry(entry.profile(), 0L);
        }
        if (entry.waitingForEmpty()) {
            return new Entry(entry.profile(), now + REFILL_MILLIS);
        }
        if (now < entry.refillAt()) {
            return entry;
        }

        int inserted = LootChestFiller.fill(level, chest, entry.profile());
        // Refill and wait for players to empty the new batch again.
        // In the unlikely case no loot could be produced, retry later.
        return new Entry(entry.profile(), inserted > 0 ? 0L : now + REFILL_MILLIS);
    }

    public void tick(ServerLevel level, long now) {
        boolean changed = false;
        Iterator<Map.Entry<Long, Entry>> iter = chests.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<Long, Entry> entry = iter.next();
            BlockPos pos = BlockPos.of(entry.getKey());
            if (!level.hasChunkAt(pos)) continue;
            if (!eligible(level, pos)) {
                // A removed / replaced / player-owned chest must never refill.
                iter.remove();
                changed = true;
                continue;
            }
            Entry advanced = advance(level, pos, entry.getValue(), now);
            if (!advanced.equals(entry.getValue())) {
                entry.setValue(advanced);
                changed = true;
            }
        }
        if (changed) setDirty();
    }

    public static LootChestData load(CompoundTag root, HolderLookup.Provider registries) {
        LootChestData data = new LootChestData();
        ListTag list = root.getList("chests", Tag.TAG_COMPOUND);
        // V1 stored a timer starting at registration/refill. Those deadlines
        // are invalid with the new rule. Preserve registered chests/profiles,
        // reset timers, and let the first observed empty state start 60 minutes.
        boolean legacy = root.getInt("formatVersion") != DATA_VERSION;
        for (Tag raw : list) {
            CompoundTag tag = (CompoundTag) raw;
            try {
                long pos = tag.getLong("pos");
                Profile profile = Profile.valueOf(tag.getString("profile"));
                long deadline = legacy ? 0L : Math.max(0L, tag.getLong("refillAt"));
                data.chests.put(pos, new Entry(profile, deadline));
            } catch (IllegalArgumentException invalid) {
                // Older / unknown profile IDs fail closed.
            }
        }
        if (legacy) data.setDirty();
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag root, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        chests.forEach((pos, entry) -> {
            CompoundTag tag = new CompoundTag();
            tag.putLong("pos", pos);
            tag.putString("profile", entry.profile().name());
            tag.putLong("refillAt", entry.refillAt());
            list.add(tag);
        });
        root.putInt("formatVersion", DATA_VERSION);
        root.put("chests", list);
        return root;
    }
}
