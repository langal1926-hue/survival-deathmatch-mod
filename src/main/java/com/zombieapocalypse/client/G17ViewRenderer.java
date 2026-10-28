package com.zombieapocalypse.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.zombieapocalypse.ZombieApocalypseMod;
import com.zombieapocalypse.item.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/**
 * Adds player-skinned pistol grip arms without replacing Minecraft's existing
 * first-person item renderer.
 *
 * The previous iteration cancelled RenderHandEvent and drew the G17 with its
 * JSON item transform *and* a second manual pose. It also drew raw PlayerRenderer
 * arms without the standard Minecraft arm-space transforms. The result was
 * enormous disconnected arms and the gun outside the screen.
 *
 * Arms use the vanilla model-space correction and *the exact same shared
 * weapon origin* as G17ClientExtensions during equip, ADS, shot and reload.
 * This prevents the gun from moving up to center while the skinned hands
 * remain down at the screen corners. The support hand rises toward the
 * magazine during its server-confirmed reload track.
 * The G17 model itself is still rendered ONCE by the vanilla item pipeline.
 */
@EventBusSubscriber(modid = ZombieApocalypseMod.MOD_ID, value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.GAME)
public final class G17ViewRenderer {
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
        G17AnimationState.Pose motion =
                G17AnimationState.sample(event.getPartialTick(), rawAds);
        int mainSide = player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;

        // IMPORTANT: gun and skin-textured arms now use the SAME calculated
        // camera-space weapon origin. The previously independent offsets
        // left the hands in the bottom corners during center-screen ADS.
        G17ClientExtensions.HeldPose held = G17ClientExtensions.calculatePose(
                rawAds, motion, mainSide, event.getEquipProgress());

        // Keep Minecraft's normal 3D item rendering. Only supply the skinned
        // arms here; never render the G17 a second time.
        drawArm(event.getPoseStack(), event.getMultiBufferSource(),
                event.getPackedLight(), renderer, player, mainSide, mainSide,
                held, false, motion);

        // In ADS, support the rear of the grip. When reloading, reach up
        // toward the magazine while the gun is moved/tilted with the same
        // timeline. Respect actual offhand items.
        if ((held.aim() > 0.08F || motion.supportReach() > 0.06F)
                && player.getOffhandItem().isEmpty()) {
            drawArm(event.getPoseStack(), event.getMultiBufferSource(),
                    event.getPackedLight(), renderer, player, -mainSide,
                    mainSide, held, true, motion);
        }
    }

    /**
     * Calibrated camera-space wrist targets relative to the exact G17 gun
     * origin. This is a lightweight first-person grip rig with no dependence
     * on external models or animation files.
     *
     * The gun geometry is anchored by G17ClientExtensions. The hands must
     * follow that SAME x/y/z when ADS, reload, equip or recoil moves the
     * weapon. Relative wrist offsets make the poses stable across states.
     */
    private static void drawArm(PoseStack poseStack, MultiBufferSource buffers,
                                int light, PlayerRenderer renderer,
                                AbstractClientPlayer player, int armSide,
                                int mainSide, G17ClientExtensions.HeldPose held,
                                boolean supporting, G17AnimationState.Pose motion) {
        poseStack.pushPose();
        float ads = held.aim();
        float x;
        float y;
        float z;

        if (!supporting) {
            // Trigger-finger grip: near the pistol's rear (+Z) handle.
            // While ADS comes in, the wrist moves LEFT and UP with the gun
            // instead of staying in the bottom-right screen corner.
            x = held.x() - mainSide * Mth.lerp(ads, 0.16F, 0.26F);
            y = held.y() + Mth.lerp(ads, 0.14F, -0.05F)
                    + motion.reload() * 0.12F;
            z = held.z() + Mth.lerp(ads, 0.08F, 0.12F);
        } else {
            // Support palm: converge below the sights during ADS. On reload,
            // bring the free hand HIGHER to the magazine well (previously it
            // was lowered by 0.22 and often vanished below the hotbar).
            float reach = motion.supportReach();
            float baseX = held.x() - mainSide * Mth.lerp(ads, 0.48F, 0.17F);
            float baseY = held.y() - Mth.lerp(ads, 0.12F, 0.14F);
            float baseZ = held.z() + 0.06F;
            x = Mth.lerp(reach, baseX, held.x() - mainSide * 0.30F);
            y = Mth.lerp(reach, baseY, held.y() + 0.34F);
            z = Mth.lerp(reach, baseZ, held.z() + 0.10F);
        }

        // The wrist targets track the same equip/lower/recoil translation as
        // the pistol. Do not subtract equipProgress a second time.
        poseStack.translate(x, y, z);

        float gripYaw = supporting ? Mth.lerp(ads, 34.0F, 20.0F)
                : Mth.lerp(ads, 39.0F, 27.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(
                armSide * gripYaw + held.yaw() * 0.45F
                        + armSide * motion.reloadAngle() * 9.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(
                held.pitch() * 0.35F - motion.shot() * 7.0F
                        - (supporting ? motion.supportReach() * 9.0F : 0.0F)));
        poseStack.mulPose(Axis.ZP.rotationDegrees(
                held.roll() * 0.22F
                        + armSide * (supporting ? 5.0F : -3.0F) * ads));
        poseStack.scale(0.82F, 0.82F, 0.82F);

        // Vanilla's required PlayerRenderer arm model coordinate correction.
        // Omitting this correction previously caused giant dislocated arms.
        poseStack.translate(armSide * -1.0F, 3.6F, 3.5F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(armSide * 120.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(200.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(armSide * -135.0F));
        poseStack.translate(armSide * 5.6F, 0.0F, 0.0F);

        if (armSide > 0) {
            renderer.renderRightHand(poseStack, buffers, light, player);
        } else {
            renderer.renderLeftHand(poseStack, buffers, light, player);
        }
        poseStack.popPose();
    }

    @SubscribeEvent
    public static void renderThirdPerson(RenderPlayerEvent.Pre event) {
        if (!event.getEntity().getMainHandItem().is(ModItems.G17_PISTOL.get())) {
            return;
        }

        // Keep the already-tested two-hand third-person aiming appearance.
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
