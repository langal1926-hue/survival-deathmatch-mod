package com.zombieapocalypse.client;

import com.zombieapocalypse.ZombieApocalypseMod;
import com.zombieapocalypse.item.ModItems;
import com.zombieapocalypse.network.GunNetwork;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.event.tick.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Weapon controls only apply when a G17 is held in the main hand.
 * Attacking with other items retains ordinary mining/combat behavior.
 */
@EventBusSubscriber(modid = ZombieApocalypseMod.MOD_ID, value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.GAME)
public final class ClientGunInput {
    private ClientGunInput() {}

    @SubscribeEvent
    public static void onAttack(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (!event.isAttack() || mc.player == null || mc.screen != null
                || !mc.player.getMainHandItem().is(ModItems.G17_PISTOL.get())) {
            return;
        }

        // Prevent the same click from doing vanilla melee damage / breaking blocks.
        event.setCanceled(true);
        event.setSwingHand(false);
        PacketDistributor.sendToServer(new GunNetwork.GunAction(false));
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (ClientGunKeys.RELOAD.consumeClick()) {
            if (mc.player != null && mc.screen == null
                    && mc.player.getMainHandItem().is(ModItems.G17_PISTOL.get())) {
                PacketDistributor.sendToServer(new GunNetwork.GunAction(true));
            }
        }
    }
}
