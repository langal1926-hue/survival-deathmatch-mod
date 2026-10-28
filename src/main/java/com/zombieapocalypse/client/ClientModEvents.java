package com.zombieapocalypse.client;

import com.zombieapocalypse.ZombieApocalypseMod;
import com.zombieapocalypse.entity.ModEntities;
import com.zombieapocalypse.item.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Reuse the vanilla zombie model and texture for the first prototype. */
@EventBusSubscriber(modid = ZombieApocalypseMod.MOD_ID, value = Dist.CLIENT)
public final class ClientModEvents {
    private ClientModEvents() {}

    /**
     * Choose our OWN model variants during local first-person animation.
     * TaCZ models/animation assets are never loaded or depended upon.
     * Baked JSON override models are rendered by Minecraft's vanilla item
     * renderer and remain unchanged for 3P, inventory or other players.
     */
    @SubscribeEvent
    public static void registerG17AnimationProperty(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(
                ModItems.G17_PISTOL.get(),
                ResourceLocation.fromNamespaceAndPath(
                        ZombieApocalypseMod.MOD_ID, "g17_frame"),
                (stack, level, entity, seed) -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (!(entity instanceof LocalPlayer)
                            || entity != mc.player
                            || !mc.options.getCameraType().isFirstPerson()
                            || !mc.player.getMainHandItem().is(ModItems.G17_PISTOL.get())) {
                        return 0.0F;
                    }
                    return G17AnimationState.modelFrame(0.5F);
                }));
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.INFECTED.get(), ZombieRenderer::new);
        event.registerEntityRenderer(ModEntities.PISTOL_BULLET.get(), ThrownItemRenderer::new);
    }
}
