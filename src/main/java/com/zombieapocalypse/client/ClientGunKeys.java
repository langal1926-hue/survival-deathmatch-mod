package com.zombieapocalypse.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.zombieapocalypse.ZombieApocalypseMod;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/** R is configurable in Minecraft's Controls menu. */
@EventBusSubscriber(modid = ZombieApocalypseMod.MOD_ID, value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class ClientGunKeys {
    public static final KeyMapping RELOAD = new KeyMapping(
            "key.zombieapocalypse.reload",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "key.categories.gameplay");

    private ClientGunKeys() {}

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(RELOAD);
    }
}
