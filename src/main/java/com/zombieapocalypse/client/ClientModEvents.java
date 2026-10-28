package com.zombieapocalypse.client;

import com.zombieapocalypse.ZombieApocalypseMod;
import com.zombieapocalypse.entity.ModEntities;
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

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.INFECTED.get(), ZombieRenderer::new);
        event.registerEntityRenderer(ModEntities.PISTOL_BULLET.get(), ThrownItemRenderer::new);
    }
}
