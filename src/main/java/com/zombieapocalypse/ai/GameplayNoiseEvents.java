package com.zombieapocalypse.ai;

import com.zombieapocalypse.ZombieApocalypseMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * Bridges actual server-side gameplay actions to the infected noise investigation AI.
 *
 * Not a general sound engine: only these explicitly handled events alert the infected.
 * Keeping this server-side prevents client duplicates in multiplayer.
 */
@EventBusSubscriber(modid = ZombieApocalypseMod.MOD_ID)
public final class GameplayNoiseEvents {
    // Initial balancing values; tune after gameplay testing.
    private static final double BREAK_HEARING_RADIUS = 16.0D;
    private static final double PLACE_HEARING_RADIUS = 10.0D;
    private static final double EXPLOSION_HEARING_RADIUS = 48.0D;

    private GameplayNoiseEvents() {}

    @SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        // The BreakEvent is player-driven; noise occurs at the affected block.
        if (event.getPlayer().isSpectator()) {
            return;
        }
        NoiseSystem.alertNearbyInfected(level, Vec3.atCenterOf(event.getPos()), BREAK_HEARING_RADIUS);
    }

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        // Avoid notifying for non-player block placement, e.g. falling blocks.
        if (!(event.getEntity() instanceof Player player) || player.isSpectator()) {
            return;
        }
        NoiseSystem.alertNearbyInfected(level, Vec3.atCenterOf(event.getPos()), PLACE_HEARING_RADIUS);
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        // Detonate runs for successful explosions, including ones which destroy no blocks.
        NoiseSystem.alertNearbyInfected(level, event.getExplosion().center(), EXPLOSION_HEARING_RADIUS);
    }
}
