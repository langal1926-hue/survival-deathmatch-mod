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
// Legacy custom-G17 code kept for world/save compatibility only.
// NOT registered with either NeoForge event bus; the separate native TaCZ
// mod owns input, R reload binding, first-person rendering and HUD.
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
