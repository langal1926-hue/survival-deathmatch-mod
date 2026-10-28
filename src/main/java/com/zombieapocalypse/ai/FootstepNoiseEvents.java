package com.zombieapocalypse.ai;

import com.zombieapocalypse.ZombieApocalypseMod;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Lightweight server-side footstep approximation.
 *
 * We sample actual position changes rather than audio playback, so client-side
 * sound options and microphone volume cannot affect the zombie AI.
 */
@EventBusSubscriber(modid = ZombieApocalypseMod.MOD_ID)
public final class FootstepNoiseEvents {
    private static final int SAMPLE_INTERVAL_TICKS = 8;
    private static final double MIN_MOVEMENT_SQUARED = 0.04D;
    private static final double MAX_MOVEMENT_SQUARED = 9.0D; // Ignore teleports / large corrections.

    // Prototype balancing values, in blocks.
    private static final double SNEAK_RADIUS = 2.5D;
    private static final double WALK_RADIUS = 7.0D;
    private static final double SPRINT_RADIUS = 16.0D;

    // Weak keys prevent long-lived references to disconnected players.
    private static final Map<ServerPlayer, Vec3> LAST_SAMPLED_POSITIONS = new WeakHashMap<>();

    private FootstepNoiseEvents() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)
                || player.tickCount % SAMPLE_INTERVAL_TICKS != 0) {
            return;
        }

        Vec3 position = player.position();
        Vec3 previous = LAST_SAMPLED_POSITIONS.put(player, position);
        if (previous == null) {
            return;
        }

        double distanceSquared = position.distanceToSqr(previous);
        if (distanceSquared < MIN_MOVEMENT_SQUARED || distanceSquared > MAX_MOVEMENT_SQUARED) {
            return;
        }

        // Walking sounds are only generated while actually moving on the ground.
        if (!player.onGround() || player.isSpectator() || player.isPassenger()
                || player.getAbilities().flying || player.isFallFlying() || player.isSwimming()
                || player.isInWater()) {
            return;
        }

        double radius = player.isCrouching() ? SNEAK_RADIUS
                : player.isSprinting() ? SPRINT_RADIUS : WALK_RADIUS;
        NoiseSystem.alertNearbyInfected(level, position, radius);
    }
}
