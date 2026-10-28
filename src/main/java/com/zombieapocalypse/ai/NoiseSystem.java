package com.zombieapocalypse.ai;

import com.zombieapocalypse.entity.InfectedZombie;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side noise notification. Future sources (gunfire, radio, voice)
 * can call this with a position and an appropriate hearing radius.
 * A sound being played does not automatically invoke this method.
 */
public final class NoiseSystem {
    private NoiseSystem() {}

    public static int alertNearbyInfected(ServerLevel level, Vec3 position, double radius) {
        AABB hearingBox = AABB.ofSize(position, radius * 2.0D, radius * 2.0D, radius * 2.0D);
        int alerted = 0;

        for (InfectedZombie infected : level.getEntitiesOfClass(InfectedZombie.class, hearingBox)) {
            if (infected.distanceToSqr(position) <= radius * radius && infected.hearNoise(position)) {
                alerted++;
            }
        }
        return alerted;
    }
}
