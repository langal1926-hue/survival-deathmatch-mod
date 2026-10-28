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
 * Camera-space transforms for the original 3D G17 model.
 *
 * The Blockbench barrel points along model +Z, whereas the Minecraft camera
 * looks down -Z. The first-person JSON display rotates the model 180 degrees
 * about Y so its sights run straight down the camera's look direction.
 *
 * In ADS the rear sight is approximately (x=1, y=21, z=-9) model units.
 * With a 0.25 item scale and a 180 degree Y rotation, its camera-relative
 * offset becomes (+0.109, +0.203, +0.266). The camera-side ADS translation
 * (-0.109, -0.203, -0.70) therefore centers the sight, rather than moving
 * the entire gun past the top of the screen.
 *
 * IMPORTANT: Return true here so the vanilla generic item-in-hand position
 * and rotations are NOT layered on top of our explicit aiming transform.
 * Minecraft still handles rendering the actual item model after this hook.
 */
@EventBusSubscriber(modid = ZombieApocalypseMod.MOD_ID, value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class G17ClientExtensions {
    private static final float HIP_X = 0.52F;
    private static final float HIP_Y = -0.50F;
    private static final float HIP_Z = -0.78F;
    private static final float ADS_X = -0.109F;
    private static final float ADS_Y = -0.203F;
    private static final float ADS_Z = -0.70F;

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
                // Don't apply main-hand ADS to a separately equipped offhand G17.
                if (!player.getMainHandItem().is(ModItems.G17_PISTOL.get())) {
                    return false;
                }

                float ads = Mth.clamp(ClientGunInput.getAimBlend(), 0.0F, 1.0F);
                int side = arm == HumanoidArm.RIGHT ? 1 : -1;

                // Interpolate actual camera-space positions. ADS_X is not
                // handedness-dependent: both sights must reach screen center.
                float x = Mth.lerp(ads, side * HIP_X, ADS_X);
                float y = Mth.lerp(ads, HIP_Y, ADS_Y) - equipProcess * 0.6F;
                float z = Mth.lerp(ads, HIP_Z, ADS_Z);
                poseStack.translate(x, y, z);

                // A little hip-fire cant; completely level/forward in ADS.
                float hip = 1.0F - ads;
                poseStack.mulPose(Axis.YP.rotationDegrees(-side * 10.0F * hip));
                poseStack.mulPose(Axis.XP.rotationDegrees(-5.0F * hip));
                poseStack.mulPose(Axis.ZP.rotationDegrees(side * 5.0F * hip));

                // Returning true skips Minecraft's additional arm/item pose
                // while preserving the JSON FIRST_PERSON item model transform.
                return true;
            }
        }, ModItems.G17_PISTOL.get());
    }
}
