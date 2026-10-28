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
 * Camera-space transforms and independent original pose animations for the
 * contributed 3D G17 model.
 *
 * The original Blockbench G17 barrel points along model -Z; the rear sight
 * sits at +Z and the front sight sits near -Z. Since Minecraft's first-person
 * camera also looks down -Z, this model must NOT be flipped 180 degrees.
 * That flip made the muzzle face the player (observed in gameplay screenshots).
 *
 * The rear sight is approximately (x=1, y=21, z=+22) model units.
 * The JSON item transform has scale 0.25. Around the vanilla item pivot (8,8,8)
 * the rear sight is (-0.109, +0.203, +0.219) relative to model center.
 * ADS translation (+0.109, -0.203, -0.70) centers the rear sight while
 * the muzzle at z≈-9 remains deeper forward along the camera's -Z axis.
 *
 * Original shoot / reload / draw keyframe tracks come from
 * G17AnimationState, not any external mod's animation files.
 *
 * IMPORTANT: Return true here so the vanilla generic item-in-hand position
 * and rotations are NOT layered on top of our explicit aiming transform.
 * The resulting held origin is also used by G17ViewRenderer for forearm grip
 * targets. Both renders sample the same item animation tracks.
 * Minecraft still handles rendering the actual item model after this hook.
 */
// Legacy custom-G17 code kept for world/save compatibility only.
// NOT registered with either NeoForge event bus; the separate native TaCZ
// mod owns input, R reload binding, first-person rendering and HUD.
public final class G17ClientExtensions {
    private static final float HIP_X = 0.52F;
    private static final float HIP_Y = -0.50F;
    private static final float HIP_Z = -0.78F;
    private static final float ADS_X = 0.109F;
    private static final float ADS_Y = -0.203F;
    private static final float ADS_Z = -0.70F;

    private G17ClientExtensions() {}

    /**
     * Camera-space origin and rotation of the G17 BEFORE JSON model scale.
     * Rendering the hands from these same coordinates prevents the situation
     * seen in the user's video where the gun reaches ADS but both hands stay
     * at the hip-fire positions.
     */
    public record HeldPose(float x, float y, float z, float yaw, float pitch,
                           float roll, float aim) {}

    public static HeldPose calculatePose(float rawAds,
                                         G17AnimationState.Pose motion,
                                         int mainSide, float equipProgress) {
        float ads = Mth.clamp(rawAds, 0.0F, 1.0F)
                * (1.0F - motion.reload() * 0.96F);

        // Preserve previously calibrated first-person iron-sight alignment,
        // barrel direction, ADS FOV and slide/magazine states.
        float x = Mth.lerp(ads, mainSide * HIP_X, ADS_X)
                + motion.bobX()
                + mainSide * motion.reloadAngle() * 0.12F;
        float y = Mth.lerp(ads, HIP_Y, ADS_Y)
                - equipProgress * 0.6F
                - motion.equip() * 0.34F
                - motion.reload() * 0.40F
                + motion.shot() * 0.09F
                - motion.bobY()
                + motion.breathing();
        float z = Mth.lerp(ads, HIP_Z, ADS_Z)
                - motion.reload() * 0.18F
                + motion.shot() * 0.15F;

        float hip = 1.0F - ads;
        float yaw = -mainSide * 10.0F * hip
                + mainSide * motion.reloadAngle() * 31.0F;
        float pitch = -5.0F * hip
                - motion.shot() * 11.0F
                - motion.reload() * 21.0F
                + motion.equip() * 19.0F;
        float roll = mainSide * (5.0F * hip
                + 16.0F * motion.reloadAngle()
                + 7.0F * motion.equip());
        return new HeldPose(x, y, z, yaw, pitch, roll, ads);
    }

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

                float rawAds = Mth.clamp(ClientGunInput.getAimBlend(), 0.0F, 1.0F);
                int side = arm == HumanoidArm.RIGHT ? 1 : -1;
                G17AnimationState.Pose motion =
                        G17AnimationState.sample(partialTick, rawAds);

                // This exact same calculated gun position is consumed by the
                // forearm renderer. The gun and hands cannot drift apart when
                // ADS, recoil, equip and reload tracks change.
                HeldPose pose = calculatePose(rawAds, motion, side, equipProcess);
                poseStack.translate(pose.x(), pose.y(), pose.z());
                poseStack.mulPose(Axis.YP.rotationDegrees(pose.yaw()));
                poseStack.mulPose(Axis.XP.rotationDegrees(pose.pitch()));
                poseStack.mulPose(Axis.ZP.rotationDegrees(pose.roll()));

                // With the optional private asset pack, apply the ACTUAL
                // TaCZ Glock 17 root/gun keyframes. The skinned hands
                // receive this identical parent motion in G17ViewRenderer.
                G17LicensedAnimation.applyGunMotion(poseStack,partialTick);

                // Returning true skips Minecraft's additional arm/item pose
                // while preserving the JSON FIRST_PERSON item model transform.
                return true;
            }
        }, ModItems.G17_PISTOL.get());
    }
}
