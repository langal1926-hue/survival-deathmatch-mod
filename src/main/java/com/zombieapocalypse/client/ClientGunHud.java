package com.zombieapocalypse.client;

import com.zombieapocalypse.ZombieApocalypseMod;
import com.zombieapocalypse.item.G17PistolItem;
import com.zombieapocalypse.item.ModItems;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * Crisp, compact native-resolution G17 HUD.
 *
 * The old HUD rendered 184x80 at fractional (2/3) scale, causing Minecraft's
 * pixel font to blur. Draw all labels at their original 1x size instead while
 * keeping the same on-screen footprint (~123x54 GUI pixels) and left position.
 */
@EventBusSubscriber(modid = ZombieApocalypseMod.MOD_ID, value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class ClientGunHud {
    private static final int WIDTH = 123;
    private static final int HEIGHT = 54;

    private static final int HOTBAR_WIDTH = 182;
    private static final int HOTBAR_HEIGHT = 22;
    private static final int HOTBAR_GAP = 6;
    private static final int MARGIN_LEFT = 6;
    private static final int MARGIN_BOTTOM = 6;
    private static final int RELOAD_LOCK_TICKS = 25;

    private static final int BACKGROUND = 0xB0141B27;
    private static final int EDGE = 0xFF364557;
    private static final int WHITE = 0xFFF3F7FD;
    private static final int MUTED = 0xFFB8C6D5;
    private static final int ACCENT = 0xFF77C7FF;
    private static final int EMPTY_COLOR = 0xFFFF7777;
    private static final int BAR_EMPTY = 0xFF3C4A59;

    private static int reloadLockTicks;

    private ClientGunHud() {}

    @SubscribeEvent
    public static void registerHud(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(ZombieApocalypseMod.MOD_ID, "g17_hud"),
                ClientGunHud::render);
    }

    /** Called only after the server has accepted and completed a reload. */
    public static void reloadConfirmed() {
        Minecraft.getInstance().execute(() -> {
            reloadLockTicks = RELOAD_LOCK_TICKS;
            // Keep the G17 cosmetic timeline in sync with server-confirmed
            // reload recovery. An unsuccessful reload does not animate.
            G17AnimationState.reloadConfirmed();
        });
    }

    /** Called by the existing client tick handler. */
    public static void clientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.player.getMainHandItem().is(ModItems.G17_PISTOL.get())) {
            reloadLockTicks = 0;
        } else if (reloadLockTicks > 0 && !mc.isPaused()) {
            reloadLockTicks--;
        }
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || mc.options.hideGui) {
            return;
        }

        ItemStack held = mc.player.getMainHandItem();
        if (!(held.getItem() instanceof G17PistolItem)) {
            return;
        }

        int loaded = G17PistolItem.getLoaded(held);
        int reserve = countReserveAmmo(mc.player);
        String reserveLabel = mc.player.getAbilities().instabuild ? "∞" : Integer.toString(reserve);

        // Keep the old display footprint and anchor next to the vanilla hotbar.
        // If the GUI is too narrow, place it ABOVE the left end of the hotbar.
        int hotbarLeft = (graphics.guiWidth() - HOTBAR_WIDTH) / 2;
        boolean fitsLeft = hotbarLeft - HOTBAR_GAP - WIDTH >= MARGIN_LEFT;
        int x = fitsLeft ? hotbarLeft - HOTBAR_GAP - WIDTH : MARGIN_LEFT;
        int y = fitsLeft
                ? graphics.guiHeight() - HEIGHT - MARGIN_BOTTOM
                : graphics.guiHeight() - HOTBAR_HEIGHT - HEIGHT - 22;
        y = Math.max(4, y);

        Font font = mc.font;

        // All geometry uses integer GUI coordinates. Text is NEVER scaled by
        // fractional pose transforms, preserving the game's native pixel font.
        graphics.fill(x, y, x + WIDTH, y + HEIGHT, BACKGROUND);
        graphics.fill(x, y, x + 2, y + HEIGHT, ACCENT);
        graphics.fill(x + 2, y, x + WIDTH, y + 1, EDGE);
        graphics.fill(x + 2, y + HEIGHT - 1, x + WIDTH, y + HEIGHT, EDGE);

        // Header: weapon name + fixed fire mode.
        graphics.drawString(font, Component.translatable("hud.zombieapocalypse.g17"),
                x + 7, y + 4, WHITE, false);
        drawRight(graphics, font, Component.translatable("hud.zombieapocalypse.fire_mode"),
                x + WIDTH - 6, y + 4, ACCENT);

        // Only the ammo number is enlarged, by an exact integer 2x scale.
        // Integer scaling keeps blocky pixel glyphs sharp (unlike 0.667x).
        graphics.pose().pushPose();
        graphics.pose().translate(x + 7, y + 16, 0);
        graphics.pose().scale(2.0F, 2.0F, 1.0F);
        graphics.drawString(font, Integer.toString(loaded), 0, 0,
                loaded == 0 ? EMPTY_COLOR : WHITE, false);
        graphics.pose().popPose();

        String reserveText = Component.translatable("hud.zombieapocalypse.reserve").getString()
                + " " + reserveLabel;
        drawRight(graphics, font, reserveText, x + WIDTH - 6, y + 22, MUTED);

        // 17 compact ammo segments fit at native pixel scale (17 * 6 = 102).
        for (int i = 0; i < G17PistolItem.MAGAZINE_SIZE; i++) {
            int segmentX = x + 7 + i * 6;
            graphics.fill(segmentX, y + 36, segmentX + 5, y + 40,
                    i < loaded ? (loaded <= 4 ? EMPTY_COLOR : ACCENT) : BAR_EMPTY);
        }
        graphics.fill(x + 7, y + 41, x + WIDTH - 6, y + 42, EDGE);

        Component status = Component.translatable(
                reloadLockTicks > 0 ? "hud.zombieapocalypse.reloading"
                        : loaded == 0 ? "hud.zombieapocalypse.empty"
                        : "hud.zombieapocalypse.ready");
        int statusColor = reloadLockTicks > 0 ? ACCENT : loaded == 0 ? EMPTY_COLOR : MUTED;
        graphics.drawString(font, status, x + 7, y + 44, statusColor, false);
        drawRight(graphics, font, Component.translatable("hud.zombieapocalypse.reload_key"),
                x + WIDTH - 6, y + 44, MUTED);

        if (reloadLockTicks > 0) {
            int length = (int) ((WIDTH - 13)
                    * (1.0F - reloadLockTicks / (float) RELOAD_LOCK_TICKS));
            graphics.fill(x + 7, y + HEIGHT - 1, x + 7 + length, y + HEIGHT, ACCENT);
        }
    }

    private static void drawRight(GuiGraphics graphics, Font font, Component label,
                                  int right, int y, int color) {
        graphics.drawString(font, label, right - font.width(label), y, color, false);
    }

    private static void drawRight(GuiGraphics graphics, Font font, String label,
                                  int right, int y, int color) {
        graphics.drawString(font, label, right - font.width(label), y, color, false);
    }

    private static int countReserveAmmo(Player player) {
        int count = 0;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(ModItems.PISTOL_AMMO.get())) {
                count += stack.getCount();
            }
        }
        return count;
    }
}
