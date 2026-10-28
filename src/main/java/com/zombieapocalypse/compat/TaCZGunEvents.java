package com.zombieapocalypse.compat;

import com.tacz.guns.api.event.common.GunFireEvent;
import com.zombieapocalypse.ZombieApocalypseMod;
import com.zombieapocalypse.ai.NoiseSystem;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.LogicalSide;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * Server-side bridge to the real, unmodified TaCZ gun engine.
 *
 * No fake G17 projectile or animation is created here: TaCZ owns its guns,
 * ammo, recoil, aiming, magazine handling, sound and full bone animations.
 * This mod only reacts to server-authorized shots for infected hearing AI.
 *
 * TaCZ 1.21.1 NeoForge fires GunFireEvent for each shot, including bursts.
 * The logical-side guard prevents double alerts from client prediction.
 */
@EventBusSubscriber(modid = ZombieApocalypseMod.MOD_ID,
        bus = EventBusSubscriber.Bus.GAME)
public final class TaCZGunEvents {
    private static final double GUNSHOT_HEARING_RADIUS = 64.0D;

    private TaCZGunEvents() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onGunFired(GunFireEvent event) {
        if (event.getLogicalSide() != LogicalSide.SERVER || event.isCanceled()) {
            return;
        }
        if (event.getShooter().level() instanceof ServerLevel level) {
            NoiseSystem.alertNearbyInfected(
                    level, event.getShooter().position(), GUNSHOT_HEARING_RADIUS);
        }
    }
}
