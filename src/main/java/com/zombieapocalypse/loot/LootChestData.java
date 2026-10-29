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
 * SavedData is dimension-local and persists the next real-world refill deadline.
 * Unloaded chunks are never loaded just to replenish supplies.
 */
public final class LootChestData extends SavedData {
    public static final long REFILL_MILLIS = 60L * 60L * 1000L;
    private static final String FILE_NAME = "zombieapocalypse_map_loot";
    private static final Factory<LootChestData> FACTORY =
            new Factory<>(LootChestData::new, LootChestData::load);

    public enum Profile {
        CIVILIAN, HOSPITAL, POLICE, MILITARY;
        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public record Entry(Profile profile, long refillAt) {}

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
        chests.put(pos.asLong(), new Entry(profile, now + REFILL_MILLIS));
        setDirty();
        return inserted;
    }

    public boolean unregister(BlockPos pos) {
        if (chests.remove(pos.asLong()) == null) return false;
        setDirty();
        return true;
    }

    /** Admin test hook. Does not overwrite existing contents. */
    public int refillNow(ServerLevel level, BlockPos pos, long now) {
        Entry entry = lookup(pos);
        if (entry == null || !eligible(level, pos)) return -1;
        ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(pos);
        if (chest == null) return -1;
        int items = LootChestFiller.fill(level, chest, entry.profile());
        chests.put(pos.asLong(), new Entry(entry.profile(), now + REFILL_MILLIS));
        setDirty();
        return items;
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
            if (entry.getValue().refillAt() > now) continue;
            if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
                LootChestFiller.fill(level, chest, entry.getValue().profile());
                entry.setValue(new Entry(entry.getValue().profile(), now + REFILL_MILLIS));
                changed = true;
            }
        }
        if (changed) setDirty();
    }

    public static LootChestData load(CompoundTag root, HolderLookup.Provider registries) {
        LootChestData data = new LootChestData();
        ListTag list = root.getList("chests", Tag.TAG_COMPOUND);
        for (Tag raw : list) {
            CompoundTag tag = (CompoundTag) raw;
            try {
                long pos = tag.getLong("pos");
                Profile profile = Profile.valueOf(tag.getString("profile"));
                long deadline = tag.getLong("refillAt");
                data.chests.put(pos, new Entry(profile, deadline));
            } catch (IllegalArgumentException invalid) {
                // Older / unknown profile IDs fail closed.
            }
        }
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
        root.put("chests", list);
        return root;
    }
}
