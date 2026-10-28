package com.zombieapocalypse.compat;

import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.event.common.GunShootEvent;
import com.zombieapocalypse.ZombieApocalypseMod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.LogicalSide;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Server-authoritative enforcement of the curated TaCZ default-pack loadouts.
 *
 * - Reject illegal TaCZ weapons and standalone accessories during pickup.
 * - Confiscate unauthorized copies added via creative/menu/commands/loot.
 * - Strip unsupported attached accessories from allowed guns without deleting
 *   the gun. Does not touch TaCZ original hand/render/audio/ballistic logic.
 * - Block shooting immediately, including weapons introduced from external
 *   mods, before the next server inventory scan.
 *
 * Only the fourteen selected gun IDs are usable. The selected two attachment
 * slots on each gun are also fixed, not just a global accessory pool.
 */
@EventBusSubscriber(modid = ZombieApocalypseMod.MOD_ID,
        bus = EventBusSubscriber.Bus.GAME)
public final class TaCZWeaponRestrictions {
    private static final int INVENTORY_SCAN_PERIOD_TICKS = 5;
    private static final Component RESTRICTED_NOTICE =
            Component.literal("서버에서 허용되지 않은 TaCZ 총기/부착물이 제거되었습니다.");

    private TaCZWeaponRestrictions() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onTrigger(GunShootEvent event) {
        if (event.getLogicalSide() == LogicalSide.SERVER
                && !TaCZWeaponAllowlist.isApprovedGunWithAttachments(event.getGunItemStack())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBullet(GunFireEvent event) {
        if (event.getLogicalSide() == LogicalSide.SERVER
                && !TaCZWeaponAllowlist.isApprovedGunWithAttachments(event.getGunItemStack())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onGroundPickup(ItemEntityPickupEvent.Pre event) {
        if (!TaCZWeaponAllowlist.isAllowedInventoryStack(
                event.getItemEntity().getItem())) {
            event.setCanPickup(TriState.FALSE);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.tickCount % INVENTORY_SCAN_PERIOD_TICKS != 0) {
            return;
        }

        int removed = 0;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) continue;

            if (!TaCZWeaponAllowlist.isAllowedInventoryStack(stack)) {
                inventory.setItem(slot, ItemStack.EMPTY);
                removed++;
            } else {
                removed += TaCZWeaponAllowlist.stripIllegalAttachments(
                        stack, player.registryAccess());
            }
        }

        // A container screen cursor is outside the 41-slot vanilla inventory.
        // Check it as well to prevent shift-click / creative inventory bypass.
        ItemStack carried = player.containerMenu.getCarried();
        if (!carried.isEmpty()) {
            if (!TaCZWeaponAllowlist.isAllowedInventoryStack(carried)) {
                player.containerMenu.setCarried(ItemStack.EMPTY);
                removed++;
            } else {
                removed += TaCZWeaponAllowlist.stripIllegalAttachments(
                        carried, player.registryAccess());
            }
        }

        if (removed > 0) {
            inventory.setChanged();
            player.containerMenu.broadcastChanges();
            player.displayClientMessage(RESTRICTED_NOTICE, true);
        }
    }
}
