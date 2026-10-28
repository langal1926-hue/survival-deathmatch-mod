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
 * Arms use the vanilla model-space correction, and their original animation
 * tracks follow G17AnimationState (equip, server-confirmed shooting/reload,
 * ADS and movement). They stay low enough not to cover the iron sights.
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
        G17AnimationState.Pose motion = G17AnimationState.sample(0.5F, rawAds);
        float ads = rawAds * (1.0F - motion.reload() * 0.96F);
        int side = player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;

        // Keep vanilla item rendering. The hands get their own synchronized
        // animation tracks but never duplicate the 3D pistol render.
        drawArm(event.getPoseStack(), event.getMultiBufferSource(),
                event.getPackedLight(), renderer, player, side, ads,
                event.getEquipProgress(), false, motion);

        // ADS: gently bring the offhand under the grip. Reload: the support
        // hand reaches toward the magazine, even when not currently ADS.
        // Avoid drawing a second hand if another item occupies the offhand.
        if ((ads > 0.08F || motion.supportReach() > 0.06F)
                && player.getOffhandItem().isEmpty()) {
            drawArm(event.getPoseStack(), event.getMultiBufferSource(),
                    event.getPackedLight(), renderer, player, -side, ads,
                    event.getEquipProgress(), true, motion);
        }
    }

    /**
     * First-person hand-space placement based on the vanilla arm renderer.
     * Its final transformation is mandatory: PlayerRenderer arm geometry is
     * in skin-model units, not world/item units.
     */
    private static void drawArm(PoseStack poseStack, MultiBufferSource buffers,
                                int light, PlayerRenderer renderer,
                                AbstractClientPlayer player, int side,
                                float ads, float equipProgress,
                                boolean supporting, G17AnimationState.Pose motion) {
        poseStack.pushPose();

        // Use compact vanilla-like coordinates so only the FOREARMS enter
        // the bottom portion of the screen, not whole oversized player arms.
        // Keep both hands BELOW the iron sights, not in front of the
        // crosshair. Hip fire is a single visible right/left forearm;
        // when aiming, the support forearm enters under the weapon.
        float horizontal = supporting
                ? Mth.lerp(ads, 0.61F, 0.25F)
                : Mth.lerp(ads, 0.60F, 0.24F);
        float vertical = supporting
                ? Mth.lerp(ads, -0.78F, -0.77F)
                : Mth.lerp(ads, -0.66F, -0.72F);
        float depth = supporting ? -0.92F : -0.84F;

        // Same 25-tick reload track drives both the 3D gun and the forearms:
        // main hand lowers with the grip, support hand reaches for magazine.
        if (supporting) {
            horizontal -= motion.supportReach() * 0.12F;
            vertical += motion.supportReach() * 0.11F;
            depth -= motion.supportReach() * 0.07F;
        } else {
            horizontal -= motion.reload() * 0.07F;
            vertical -= motion.reload() * 0.25F;
            vertical += motion.shot() * 0.055F;
            vertical -= motion.equip() * 0.18F;
            depth += motion.shot() * 0.065F;
        }

        poseStack.translate(side * (horizontal + motion.bobX() * 0.5F),
                vertical - equipProgress * 0.50F - motion.bobY() * 0.5F,
                depth);
        float gripYaw = supporting ? Mth.lerp(ads, 36.0F, 18.0F)
                : Mth.lerp(ads, 40.0F, 18.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(side
                * (gripYaw + motion.reloadAngle() * (supporting ? 7.0F : 13.0F))));
        poseStack.mulPose(Axis.XP.rotationDegrees(
                -motion.shot() * 5.0F + motion.reload() * 6.0F));
        poseStack.scale(0.88F, 0.88F, 0.88F);

        // Vanilla's established PlayerRenderer.renderRight/LeftHand matrix.
        // Omitting this block was responsible for the giant dislocated hands.
        poseStack.translate(side * -1.0F, 3.6F, 3.5F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * 120.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(200.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(side * -135.0F));
        poseStack.translate(side * 5.6F, 0.0F, 0.0F);

        if (side > 0) {
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
