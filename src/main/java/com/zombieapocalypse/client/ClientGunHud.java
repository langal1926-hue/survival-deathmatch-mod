package com.zombieapocalypse.client;

import com.zombieapocalypse.ZombieApocalypseMod;
import com.zombieapocalypse.item.G17PistolItem;
import com.zombieapocalypse.item.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.DeltaTracker;
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
 * Minimal, client-only weapon HUD. Displays actual synchronized item magazine
 * data and inventory reserve ammunition. No polling the server is necessary.
 *
 * Reload is currently immediate on the server with a 25-tick lockout. The
 * confirmation packet starts a matching visual cooldown indicator here.
 */
@EventBusSubscriber(modid = ZombieApocalypseMod.MOD_ID, value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class ClientGunHud {
    private static final int WIDTH = 184;
    private static final int HEIGHT = 80;
    private static final int MARGIN_RIGHT = 12;
    private static final int MARGIN_BOTTOM = 48;
    private static final int RELOAD_LOCK_TICKS = 25;

    private static final int BACKGROUND = 0xD8141B27;
    private static final int EDGE = 0xFF364557;
    private static final int WHITE = 0xFFF3F7FD;
    private static final int MUTED = 0xFF9CABBE;
    private static final int ACCENT = 0xFF63B8F7;
    private static final int EMPTY_COLOR = 0xFFEC6868;
    private static final int BAR_EMPTY = 0xFF3C4A59;

    private static int reloadLockTicks;

    private ClientGunHud() {}

    @SubscribeEvent
    public static void registerHud(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(ZombieApocalypseMod.MOD_ID, "g17_hud"),
                ClientGunHud::render);
    }

    /** Called after the server has accepted and applied a reload. */
    public static void reloadConfirmed() {
        Minecraft.getInstance().execute(() -> reloadLockTicks = RELOAD_LOCK_TICKS);
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

        int x = graphics.guiWidth() - WIDTH - MARGIN_RIGHT;
        int y = graphics.guiHeight() - HEIGHT - MARGIN_BOTTOM;
        Font font = mc.font;

        // Frame and left accent. Keep the panel clear of the crosshair and hotbar.
        graphics.fill(x, y, x + WIDTH, y + HEIGHT, BACKGROUND);
        graphics.fill(x, y, x + 2, y + HEIGHT, ACCENT);
        graphics.fill(x + 2, y, x + WIDTH, y + 1, EDGE);
        graphics.fill(x + 2, y + HEIGHT - 1, x + WIDTH, y + HEIGHT, EDGE);

        graphics.drawString(font, Component.translatable("hud.zombieapocalypse.g17"),
                x + 11, y + 7, WHITE, false);
        drawRight(graphics, font, Component.translatable("hud.zombieapocalypse.fire_mode"),
                x + WIDTH - 10, y + 7, ACCENT);

        // Big loaded count, with reserve displayed underneath a divider.
        graphics.pose().pushPose();
        graphics.pose().translate(x + 11, y + 21, 0);
        graphics.pose().scale(1.75F, 1.75F, 1.0F);
        graphics.drawString(font, Integer.toString(loaded), 0, 0,
                loaded == 0 ? EMPTY_COLOR : WHITE, false);
        graphics.pose().popPose();

        String reserveText = Component.translatable("hud.zombieapocalypse.reserve").getString()
                + "  " + reserveLabel;
        drawRight(graphics, font, reserveText, x + WIDTH - 10, y + 28, MUTED);

        // Individual rounds make the 17-round capacity readable at a glance.
        int barX = x + 11;
        int barY = y + 46;
        for (int i = 0; i < G17PistolItem.MAGAZINE_SIZE; i++) {
            int sx = barX + i * 9;
            graphics.fill(sx, barY, sx + 7, barY + 5,
                    i < loaded ? (loaded <= 4 ? EMPTY_COLOR : ACCENT) : BAR_EMPTY);
        }

        graphics.fill(x + 11, y + 58, x + WIDTH - 10, y + 59, EDGE);

        if (reloadLockTicks > 0) {
            String status = Component.translatable("hud.zombieapocalypse.reloading").getString();
            graphics.drawString(font, status, x + 11, y + 63, ACCENT, false);
            // Shows recovery after a reload, not a second ammunition transfer.
            int progress = (int) ((WIDTH - 22) * (1.0F - reloadLockTicks / (float) RELOAD_LOCK_TICKS));
            graphics.fill(x + 11, y + HEIGHT - 3, x + 11 + progress, y + HEIGHT - 1, ACCENT);
        } else if (loaded == 0) {
            graphics.drawString(font, Component.translatable("hud.zombieapocalypse.empty"),
                    x + 11, y + 63, EMPTY_COLOR, false);
        } else {
            graphics.drawString(font, Component.translatable("hud.zombieapocalypse.ready"),
                    x + 11, y + 63, MUTED, false);
        }
        drawRight(graphics, font, Component.translatable("hud.zombieapocalypse.reload_key"),
                x + WIDTH - 10, y + 63, MUTED);
    }

    private static void drawRight(GuiGraphics graphics, Font font, Component text,
                                  int right, int y, int color) {
        graphics.drawString(font, text, right - font.width(text), y, color, false);
    }

    private static void drawRight(GuiGraphics graphics, Font font, String text,
                                  int right, int y, int color) {
        graphics.drawString(font, text, right - font.width(text), y, color, false);
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
