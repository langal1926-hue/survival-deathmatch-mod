package com.zombieapocalypse.item;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;

/**
 * Short-lived server-side ADS state.
 *
 * The client sends right-click held updates every five ticks. An old ADS flag
 * expires automatically when updates stop (disconnect, menu, item swap).
 * Only a player actually holding the G17 can be marked as aiming.
 */
public final class GunAimState {
    private static final int TIMEOUT_TICKS = 12;
    private static final Map<ServerPlayer, Long> LAST_AIM_TICK = new WeakHashMap<>();

    private GunAimState() {}

    public static void update(ServerPlayer player, boolean aiming) {
        if (!aiming || player.isSpectator()
                || !(player.getMainHandItem().getItem() instanceof G17PistolItem)) {
            LAST_AIM_TICK.remove(player);
            return;
        }
        LAST_AIM_TICK.put(player, player.serverLevel().getGameTime());
    }

    public static boolean isAiming(ServerPlayer player) {
        if (player.isSpectator() || player.isSprinting()
                || !(player.getMainHandItem().getItem() instanceof G17PistolItem)) {
            return false;
        }
        Long time = LAST_AIM_TICK.get(player);
        if (time == null) {
            return false;
        }
        long age = player.serverLevel().getGameTime() - time;
        return age >= 0 && age <= TIMEOUT_TICKS;
    }
}
