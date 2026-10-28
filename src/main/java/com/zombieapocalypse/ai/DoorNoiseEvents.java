package com.zombieapocalypse.ai;

import com.zombieapocalypse.ZombieApocalypseMod;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Emits door movement noises only after a real OPEN property change.
 *
 * RightClickBlock fires before Minecraft processes the interaction, so checking
 * the block state immediately would give false positives (e.g. locked iron doors).
 * We compare the old/new block states during the next server level tick.
 */
@EventBusSubscriber(modid = ZombieApocalypseMod.MOD_ID)
public final class DoorNoiseEvents {
    private static final double HEARING_RADIUS = 12.0D;
    private static final Map<ServerLevel, Map<BlockPos, PendingDoor>> PENDING = new WeakHashMap<>();

    private DoorNoiseEvents() {}

    private record PendingDoor(boolean wasOpen, long observedAtGameTime) {}

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)
                || event.getEntity().isSpectator()) {
            return;
        }

        BlockPos pos = event.getPos().immutable();
        BlockState state = level.getBlockState(pos);
        if (!isDoorLike(state)) {
            return;
        }

        // The two hands can produce separate interaction events; one pending
        // observation per door block avoids emitting duplicate hearing events.
        PENDING.computeIfAbsent(level, ignored -> new HashMap<>())
                .putIfAbsent(pos, new PendingDoor(
                        state.getValue(BlockStateProperties.OPEN), level.getGameTime()));
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        Map<BlockPos, PendingDoor> observations = PENDING.get(level);
        if (observations == null || observations.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<BlockPos, PendingDoor>> iterator = observations.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, PendingDoor> entry = iterator.next();
            PendingDoor pending = entry.getValue();

            if (level.getGameTime() <= pending.observedAtGameTime()) {
                continue;
            }

            BlockState after = level.getBlockState(entry.getKey());
            if (isDoorLike(after) && after.getValue(BlockStateProperties.OPEN) != pending.wasOpen()) {
                NoiseSystem.alertNearbyInfected(level, Vec3.atCenterOf(entry.getKey()), HEARING_RADIUS);
            }
            iterator.remove();
        }

        if (observations.isEmpty()) {
            PENDING.remove(level);
        }
    }

    private static boolean isDoorLike(BlockState state) {
        return state.hasProperty(BlockStateProperties.OPEN)
                && (state.getBlock() instanceof DoorBlock
                    || state.getBlock() instanceof TrapDoorBlock
                    || state.getBlock() instanceof FenceGateBlock);
    }
}
