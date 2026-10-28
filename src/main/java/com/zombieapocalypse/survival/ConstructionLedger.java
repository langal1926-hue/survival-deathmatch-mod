package com.zombieapocalypse.survival;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Server-only, dimension-local record of blocks that players built.
 * Any position absent from this registry is treated as pre-existing map terrain.
 * The ledger survives world restarts; it never trusts client-supplied ownership.
 */
public final class ConstructionLedger extends SavedData {
    private static final String FILE_NAME = "zombieapocalypse_player_builds";
    private static final Factory<ConstructionLedger> FACTORY =
            new Factory<>(ConstructionLedger::new, ConstructionLedger::load);

    private final Map<Long, UUID> owners = new HashMap<>();

    public static ConstructionLedger get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, FILE_NAME);
    }

    public UUID owner(BlockPos pos) {
        return owners.get(pos.asLong());
    }

    public void record(BlockPos pos, UUID owner) {
        owners.put(pos.asLong(), owner);
        setDirty();
    }

    public void forget(BlockPos pos) {
        if (owners.remove(pos.asLong()) != null) {
            setDirty();
        }
    }

    public static ConstructionLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        ConstructionLedger data = new ConstructionLedger();
        ListTag entries = tag.getList("placed", Tag.TAG_COMPOUND);
        for (Tag raw : entries) {
            CompoundTag entry = (CompoundTag) raw;
            if (entry.hasUUID("owner") && entry.contains("pos", Tag.TAG_LONG)) {
                data.owners.put(entry.getLong("pos"), entry.getUUID("owner"));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        owners.forEach((position, owner) -> {
            CompoundTag entry = new CompoundTag();
            entry.putLong("pos", position);
            entry.putUUID("owner", owner);
            entries.add(entry);
        });
        tag.put("placed", entries);
        return tag;
    }
}
