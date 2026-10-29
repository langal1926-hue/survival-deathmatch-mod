package com.zombieapocalypse.loot;

import com.zombieapocalypse.ZombieApocalypseMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Chunk-safe world ticking and stale designation removal on player edits.
 * Only the explicit SavedData whitelist is inspected, never all map chests.
 */
@EventBusSubscriber(modid = ZombieApocalypseMod.MOD_ID)
public final class LootChestEvents {
    private LootChestEvents() {}

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level && level.getGameTime() % 100L == 0L) {
            LootChestData.get(level).tick(level, System.currentTimeMillis());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!event.isCanceled() && event.getLevel() instanceof ServerLevel level) {
            LootChestData.get(level).unregister(event.getPos());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!event.isCanceled() && event.getLevel() instanceof ServerLevel level
                && event.getEntity() instanceof ServerPlayer) {
            LootChestData.get(level).unregister(event.getPos());
        }
    }
}
