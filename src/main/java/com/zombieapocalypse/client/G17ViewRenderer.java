package com.zombieapocalypse.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.zombieapocalypse.ZombieApocalypseMod;
import com.zombieapocalypse.item.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/**
 * G17 first-person grip rig. Uses two independently placed player-skin arms
 * parented to the actual animated gun pose, not free-floating screen positions.
 *
 * The earlier renderer moved the item and wrists in separately calculated
 * camera positions. A primary hand was mistakenly placed LEFT of a right-hand
 * gun in ADS, and the oversized support hand overlapped the slide. The
 * reference TaCZ gun rig gives distinct right/left hand nodes under a shared
 * moving gun parent; this independent implementation follows that design.
 *
 * No third-party hand model, animation, transforms or assets are copied.
 * PlayerRenderer supplies the player's real skin and sleeve textures.
 */
@EventBusSubscriber(modid = ZombieApocalypseMod.MOD_ID, value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.GAME)
public final class G17ViewRenderer {
    // Original camera-space grip positions relative to the already calibrated
    // G17 item pivot (its model contains a rear grip at model +Z). A positive
    // X for a RIGHT-handed main hand is deliberate: the previous sign was
    // negative and made the right hand look like the left hand in ADS.
    private static final float MAIN_HIP_X = 0.22F;
    private static final float MAIN_ADS_X = 0.17F;
    private static final float MAIN_HIP_Y = -0.25F;
    private static final float MAIN_ADS_Y = -0.27F;
    private static final float MAIN_Z = 0.08F;

    private static final float SUPPORT_HIP_X = 0.27F;
    private static final float SUPPORT_ADS_X = 0.19F;
    private static final float SUPPORT_HIP_Y = -0.35F;
    private static final float SUPPORT_ADS_Y = -0.34F;
    private static final float SUPPORT_Z = -0.05F;

    // Vanilla PlayerRenderer first-person arms are quite bulky relative to a
    // pistol. A smaller mesh prevents hiding the slide and iron sights.
    private static final float GRIP_ARM_SCALE = 0.69F;

    private G17ViewRenderer() {}

    @SubscribeEvent
    public static void renderFirstPerson(RenderHandEvent event) {
        if (event.getHand() != InteractionHand.MAIN_HAND
                || !event.getItemStack().is(ModItems.G17_PISTOL.get())) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.isInvisible()
                || !mc.options.getCameraType().isFirstPerson()) {
            return;
        }

        AbstractClientPlayer player = mc.player;
        PlayerRenderer renderer = (PlayerRenderer) mc.getEntityRenderDispatcher()
                .getRenderer(player);
        float rawAds = Mth.clamp(ClientGunInput.getAimBlend(), 0.0F, 1.0F);
        G17AnimationState.Pose animation =
                G17AnimationState.sample(event.getPartialTick(), rawAds);
        int mainSide = player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;

        // Exactly the transform used by the gun item renderer. Local wrist
        // transforms below are children of it, so they follow rotation as
        // well as position during draw, ADS, shots and magazine handling.
        G17ClientExtensions.HeldPose held = G17ClientExtensions.calculatePose(
                rawAds, animation, mainSide, event.getEquipProgress());

        drawRiggedArm(event.getPoseStack(), event.getMultiBufferSource(),
                event.getPackedLight(), renderer, player, mainSide, mainSide,
                false, held, animation);

        // Unlike the earlier always-visible left fist, the support arm
        // appears with ADS and while handling the magazine. It is deliberately
        // smaller and behind/below the sights, never in the bore line.
        if (player.getOffhandItem().isEmpty()
                && (held.aim() > 0.20F || animation.supportReach() > 0.05F)) {
            drawRiggedArm(event.getPoseStack(), event.getMultiBufferSource(),
                    event.getPackedLight(), renderer, player, -mainSide,
                    mainSide, true, held, animation);
        }

        // Do NOT cancel RenderHandEvent: Minecraft draws the real 3D G17 once
        // afterward, retaining the working iron-sight calibration and model
        // override states (slide/magazine), without a duplicate gun.
    }

    private static void drawRiggedArm(PoseStack poseStack,
                                      MultiBufferSource buffer,
                                      int packedLight,
                                      PlayerRenderer renderer,
                                      AbstractClientPlayer player,
                                      int armSide,
                                      int mainSide,
                                      boolean support,
                                      G17ClientExtensions.HeldPose held,
                                      G17AnimationState.Pose motion) {
        poseStack.pushPose();

        // First apply the *actual* weapon transform. This reproduces a
        // simple parent/child bone hierarchy and removes camera-space drift.
        poseStack.translate(held.x(), held.y(), held.z());
        poseStack.mulPose(Axis.YP.rotationDegrees(held.yaw()));
        poseStack.mulPose(Axis.XP.rotationDegrees(held.pitch()));
        poseStack.mulPose(Axis.ZP.rotationDegrees(held.roll()));

        float ads = held.aim();
        float x;
        float y;
        float z;
        if (!support) {
            // Trigger hand is on the RIGHT of the pistol when mainSide=+1.
            // It holds the rear grip, not the slide. Mirrored for left-handed
            // players and slightly inboard during ADS.
            x = mainSide * Mth.lerp(ads, MAIN_HIP_X, MAIN_ADS_X);
            y = Mth.lerp(ads, MAIN_HIP_Y, MAIN_ADS_Y);
            z = MAIN_Z;
        } else {
            // The other hand stays on the OPPOSITE side. During reload its
            // wrist rises to the magazine well while its animated parent
            // gun lowers. This avoids the hand disappearing below the HUD.
            float reach = motion.supportReach();
            x = -mainSide * Mth.lerp(ads, SUPPORT_HIP_X, SUPPORT_ADS_X);
            y = Mth.lerp(ads, SUPPORT_HIP_Y, SUPPORT_ADS_Y);
            z = SUPPORT_Z;

            x = Mth.lerp(reach, x, -mainSide * 0.13F);
            y = Mth.lerp(reach, y, 0.44F);
            z = Mth.lerp(reach, z, 0.10F);
        }

        poseStack.translate(x + motion.bobX() * 0.15F,
                y - motion.bobY() * 0.15F, z);

        // Local wrist pitch: the arms start BELOW the gun and travel up to
        // meet the grip. They are not allowed to swing in front of the sights.
        float localYaw = support
                ? Mth.lerp(ads, 31.0F, 17.0F)
                : Mth.lerp(ads, 34.0F, 21.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(armSide * localYaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(
                support ? (-18.0F - 10.0F * motion.supportReach()) : -6.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(
                armSide * (support ? 10.0F : -4.0F)));

        // Keep the actual player skin / sleeves, just decrease geometry
        // footprint to fit a one-block pistol. Scale BEFORE the vanilla arm
        // model-to-camera-space correction.
        poseStack.scale(GRIP_ARM_SCALE, GRIP_ARM_SCALE, GRIP_ARM_SCALE);

        // Minecraft's first-person arm-model correction, not a TaCZ rig.
        poseStack.translate(armSide * -1.0F, 3.6F, 3.5F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(armSide * 120.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(200.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(armSide * -135.0F));
        poseStack.translate(armSide * 5.6F, 0.0F, 0.0F);

        if (armSide == 1) {
            renderer.renderRightHand(poseStack, buffer, packedLight, player);
        } else {
            renderer.renderLeftHand(poseStack, buffer, packedLight, player);
        }
        poseStack.popPose();
    }

    @SubscribeEvent
    public static void renderThirdPerson(RenderPlayerEvent.Pre event) {
        if (!event.getEntity().getMainHandItem().is(ModItems.G17_PISTOL.get())) {
            return;
        }

        // Preserve the previously accepted multiplayer third-person aim pose.
        var model = event.getRenderer().getModel();
        if (event.getEntity().getMainArm() == HumanoidArm.RIGHT) {
            model.rightArmPose = HumanoidModel.ArmPose.CROSSBOW_HOLD;
            model.leftArmPose = HumanoidModel.ArmPose.EMPTY;
        } else {
            model.leftArmPose = HumanoidModel.ArmPose.CROSSBOW_HOLD;
            model.rightArmPose = HumanoidModel.ArmPose.EMPTY;
        }
    }
}
