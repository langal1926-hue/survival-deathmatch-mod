package com.zombieapocalypse.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.zombieapocalypse.ZombieApocalypseMod;
import com.zombieapocalypse.item.ModItems;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * Client-only ADS transform for the held G17.
 *
 * Unlike a replacement RenderHandEvent renderer, this hook runs at the
 * vanilla item-transformation stage. Returning false deliberately retains
 * the standard vanilla equip/swing handling and applies our ADS movement
 * only once. The item's own first-person JSON transform supplies its scale.
 */
@EventBusSubscriber(modid = ZombieApocalypseMod.MOD_ID, value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class G17ClientExtensions {
    private G17ClientExtensions() {}

    @SubscribeEvent
    public static void register(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public boolean applyForgeHandTransform(PoseStack poseStack,
                                                   LocalPlayer player,
                                                   HumanoidArm arm,
                                                   ItemStack itemInHand,
                                                   float partialTick,
                                                   float equipProcess,
                                                   float swingProcess) {
                float ads = Mth.clamp(ClientGunInput.getAimBlend(), 0.0F, 1.0F);
                if (ads > 0.001F) {
                    int side = arm == HumanoidArm.RIGHT ? 1 : -1;
                    // Shift from normal right-lower hip fire to centered ADS.
                    // The vanilla pose is still responsible for the base
                    // position; no extra renderItem() invocation occurs.
                    poseStack.translate(-side * 0.36F * ads,
                            0.045F * ads,
                            -0.11F * ads);
                    poseStack.mulPose(Axis.YP.rotationDegrees(-side * 14.0F * ads));
                    poseStack.mulPose(Axis.XP.rotationDegrees(-3.0F * ads));
                }
                return false;
            }
        }, ModItems.G17_PISTOL.get());
    }
}
