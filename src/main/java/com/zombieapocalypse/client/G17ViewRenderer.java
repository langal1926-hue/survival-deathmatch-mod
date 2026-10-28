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
 * This implementation renders the arms with the same model-space correction as
 * vanilla ItemInHandRenderer.renderPlayerArm, while allowing the game to draw
 * the pistol normally afterward. ADS item movement is handled separately
 * through G17ClientExtensions, once and only once.
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
        float ads = Mth.clamp(ClientGunInput.getAimBlend(), 0.0F, 1.0F);
        int side = player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;

        // Critically: do NOT cancel the event. Vanilla renders the G17 after
        // these arms, avoiding duplicate item transforms and sky-sized models.
        drawArm(event.getPoseStack(), event.getMultiBufferSource(),
                event.getPackedLight(), renderer, player, side, ads,
                event.getEquipProgress(), false);

        // Hip-fire is one-handed. Only add the support arm during ADS, and
        // never if the player is already holding something in that hand.
        if (ads > 0.08F && player.getOffhandItem().isEmpty()) {
            drawArm(event.getPoseStack(), event.getMultiBufferSource(),
                    event.getPackedLight(), renderer, player, -side, ads,
                    event.getEquipProgress(), true);
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
                                boolean supporting) {
        poseStack.pushPose();

        // Use compact vanilla-like coordinates so only the FOREARMS enter
        // the bottom portion of the screen, not whole oversized player arms.
        float horizontal = supporting
                ? Mth.lerp(ads, 0.64F, 0.37F)
                : Mth.lerp(ads, 0.64F, 0.36F);
        float vertical = supporting
                ? Mth.lerp(ads, -0.64F, -0.47F)
                : Mth.lerp(ads, -0.60F, -0.46F);
        float depth = supporting ? -0.80F : -0.72F;
        poseStack.translate(side * horizontal,
                vertical - equipProgress * 0.50F, depth);
        poseStack.mulPose(Axis.YP.rotationDegrees(
                side * (supporting ? Mth.lerp(ads, 42.0F, 23.0F)
                        : Mth.lerp(ads, 45.0F, 27.0F))));

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
