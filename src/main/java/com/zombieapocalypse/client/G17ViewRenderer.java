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
 * Arms use the vanilla model-space correction but stay low enough to avoid
 * blocking the center-aligned iron sights. The actual pistol is rendered once
 * through the vanilla item pipeline; G17ClientExtensions owns its complete
 * hip-fire/ADS camera-space item transform.
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

        // Do NOT cancel the event. Vanilla renders the G17 after
        // these arms, preventing duplicate item rendering.
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
        poseStack.translate(side * horizontal,
                vertical - equipProgress * 0.50F, depth);
        poseStack.mulPose(Axis.YP.rotationDegrees(
                side * (supporting ? Mth.lerp(ads, 36.0F, 18.0F)
                        : Mth.lerp(ads, 40.0F, 18.0F))));
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
