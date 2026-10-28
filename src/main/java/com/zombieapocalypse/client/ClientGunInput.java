package com.zombieapocalypse.client;

import com.zombieapocalypse.ZombieApocalypseMod;
import com.zombieapocalypse.item.ModItems;
import com.zombieapocalypse.network.GunNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * G17: left-click fire, R reload, hold right-click for ADS.
 *
 * ADS FOV and recoil are presentation-only; authoritative bullet direction
 * and spread are computed by the server. Aiming updates have a server timeout.
 */
@EventBusSubscriber(modid = ZombieApocalypseMod.MOD_ID, value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.GAME)
public final class ClientGunInput {
    private static final float ADS_ZOOM = 0.70F;
    private static final int AIM_HEARTBEAT_TICKS = 5;

    private static boolean aimSent;
    private static int heartbeat;
    private static float adsBlend;

    private ClientGunInput() {}

    /** Smoothed ADS fraction shared by first-person arms and pistol position. */
    public static float getAimBlend() {
        return adsBlend;
    }

    private static boolean holdingG17(Minecraft mc) {
        return mc.player != null && mc.screen == null
                && mc.player.getMainHandItem().is(ModItems.G17_PISTOL.get());
    }

    @SubscribeEvent
    public static void onWeaponInput(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (!holdingG17(mc)) {
            return;
        }

        if (event.isAttack()) {
            // G17 left-clicks should not simultaneously mine or melee.
            event.setCanceled(true);
            event.setSwingHand(false);
            PacketDistributor.sendToServer(new GunNetwork.GunAction(false));
        } else if (event.isUseItem()) {
            // Right-click controls ADS, not normal block/item interactions
            // while a G17 is held. The held key is sampled on client ticks.
            event.setCanceled(true);
            event.setSwingHand(false);
            if (!mc.player.isSprinting() && !aimSent) {
                aimSent = true;
                heartbeat = 0;
                PacketDistributor.sendToServer(new GunNetwork.AimUpdate(true));
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        G17AnimationState.tick();
        ClientGunHud.clientTick();
        Minecraft mc = Minecraft.getInstance();
        boolean holding = holdingG17(mc);
        boolean aiming = holding && !mc.player.isSprinting() && mc.options.keyUse.isDown();

        if (mc.getConnection() != null) {
            if (aiming != aimSent) {
                aimSent = aiming;
                heartbeat = 0;
                PacketDistributor.sendToServer(new GunNetwork.AimUpdate(aiming));
            } else if (aiming && ++heartbeat >= AIM_HEARTBEAT_TICKS) {
                heartbeat = 0;
                PacketDistributor.sendToServer(new GunNetwork.AimUpdate(true));
            }
        } else {
            aimSent = false;
            heartbeat = 0;
        }

        // Smooth in/out of ADS over several frames without changing game FOV settings.
        adsBlend = Mth.lerp(0.24F, adsBlend, aiming ? 1.0F : 0.0F);

        while (ClientGunKeys.RELOAD.consumeClick()) {
            if (holding && mc.getConnection() != null) {
                PacketDistributor.sendToServer(new GunNetwork.GunAction(true));
            }
        }
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.options.getCameraType().isFirstPerson() && adsBlend > 0.001F) {
            double factor = 1.0D - (1.0D - ADS_ZOOM) * adsBlend;
            event.setFOV(event.getFOV() * factor);
        }
    }

    /** Recoil runs only after the server confirms a real bullet was spawned. */
    public static void applyRecoil(boolean aimed) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player == null || !mc.player.getMainHandItem().is(ModItems.G17_PISTOL.get())) {
                return;
            }

            // Fire arm/gun recoil only when the server accepted a REAL shot.
            G17AnimationState.shotConfirmed();
            float pitchKick = aimed ? 0.85F : 1.85F;
            float yawKick = (mc.player.getRandom().nextFloat() - 0.5F) * (aimed ? 0.5F : 1.4F);
            mc.player.setXRot(Mth.clamp(mc.player.getXRot() - pitchKick, -90.0F, 90.0F));
            mc.player.setYRot(mc.player.getYRot() + yawKick);
        });
    }
}
