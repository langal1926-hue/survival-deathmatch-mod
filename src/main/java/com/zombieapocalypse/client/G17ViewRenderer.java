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
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/**
 * G17 presentation only. The physical bullets and damage stay server-side.
 *
 * First person: replace the usual oversized item transform with a small gun
 * held by the player's skin-textured arm(s). Holding ADS slides the pistol
 * toward the crosshair while both arms follow it.
 *
 * Third person: use vanilla crossbow-hold arm animation for a two-hand pistol
 * aim, including other players in multiplayer. No custom skeleton or texture
 * replacement is necessary.
 */
@EventBusSubscriber(modid = ZombieApocalypseMod.MOD_ID, value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.GAME)
public final class G17ViewRenderer {
    private G17ViewRenderer() {}

    @SubscribeEvent
    public static void renderFirstPerson(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getHand() != InteractionHand.MAIN_HAND
                || mc.player == null
                || !event.getItemStack().is(ModItems.G17_PISTOL.get())) {
            return;
        }

        // Only replace the local first-person main hand. The offhand is still
        // rendered normally if the player has another item equipped.
        event.setCanceled(true);
        AbstractClientPlayer player = mc.player;
        PlayerRenderer playerRenderer = (PlayerRenderer) mc.getEntityRenderDispatcher()
                .getRenderer(player);
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource buffers = event.getMultiBufferSource();
        int light = event.getPackedLight();
        float ads = Mth.clamp(ClientGunInput.getAimBlend(), 0.0F, 1.0F);
        float equip = event.getEquipProgress();

        boolean rightHanded = player.getMainArm() == HumanoidArm.RIGHT;
        int side = rightHanded ? 1 : -1;

        // Share the same equip lowering between the hands and pistol.
        poseStack.pushPose();
        poseStack.translate(0.0F, -equip * 0.45F, 0.0F);

        if (!player.isInvisible()) {
            renderShootingArm(poseStack, buffers, light, player, playerRenderer, side, ads);

            // Draw the support hand only when the offhand is empty, avoiding
            // duplicate arms over an actual offhand item.
            if (player.getOffhandItem().isEmpty()) {
                renderSupportArm(poseStack, buffers, light, player, playerRenderer, -side, ads);
            }
        }

        // Original model/texture is still rendered by Minecraft's item
        // renderer, with the FIRST_PERSON display transform from its JSON.
        // The smaller offsets make the grip/arm visible in hip fire; ADS
        // shifts the sights close to screen center, like a two-handed FPS.
        poseStack.pushPose();
        poseStack.translate(side * Mth.lerp(ads, 0.11F, -0.29F),
                Mth.lerp(ads, -0.02F, 0.10F),
                Mth.lerp(ads, -0.24F, -0.42F));
        poseStack.mulPose(Axis.YP.rotationDegrees(side * Mth.lerp(ads, -8.0F, -26.0F)));
        poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(ads, 1.0F, -4.0F)));

        mc.getEntityRenderDispatcher().getItemInHandRenderer().renderItem(
                player, event.getItemStack(),
                rightHanded ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                        : ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                !rightHanded, poseStack, buffers, light);

        poseStack.popPose();
        poseStack.popPose();
    }

    private static void renderShootingArm(PoseStack poseStack, MultiBufferSource buffers,
                                           int light, AbstractClientPlayer player,
                                           PlayerRenderer renderer, int side, float ads) {
        poseStack.pushPose();
        poseStack.translate(side * Mth.lerp(ads, 0.67F, 0.30F),
                Mth.lerp(ads, -0.70F, -0.48F), -0.85F);
        poseStack.mulPose(Axis.YP.rotationDegrees(side * Mth.lerp(ads, 43.0F, 19.0F)));
        poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(ads, -50.0F, -33.0F)));
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * Mth.lerp(ads, 26.0F, 13.0F)));
        if (side > 0) {
            renderer.renderRightHand(poseStack, buffers, light, player);
        } else {
            renderer.renderLeftHand(poseStack, buffers, light, player);
        }
        poseStack.popPose();
    }

    private static void renderSupportArm(PoseStack poseStack, MultiBufferSource buffers,
                                          int light, AbstractClientPlayer player,
                                          PlayerRenderer renderer, int side, float ads) {
        poseStack.pushPose();
        poseStack.translate(side * Mth.lerp(ads, 0.68F, 0.29F),
                Mth.lerp(ads, -0.90F, -0.53F),
                Mth.lerp(ads, -0.85F, -0.78F));
        poseStack.mulPose(Axis.YP.rotationDegrees(side * Mth.lerp(ads, 52.0F, 23.0F)));
        poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(ads, -69.0F, -44.0F)));
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * Mth.lerp(ads, 33.0F, 17.0F)));
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

        // Crossbow-hold poses both vanilla player arms forward. Unlike a
        // custom renderer, this respects player skins, sleeves and slim arms.
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
