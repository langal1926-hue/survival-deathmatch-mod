package com.zombieapocalypse.rules;

import com.zombieapocalypse.ZombieApocalypseMod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.CrafterMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.brewing.PotionBrewEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Loot and trade supply the server's resources; players cannot mine or manufacture them. */
@EventBusSubscriber(modid = ZombieApocalypseMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class SurvivalRestrictions {
    private SurvivalRestrictions() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        // Creative is an explicit map-editing exception. OP status alone is not a bypass.
        if (!event.getPlayer().isCreative()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onStartMining(PlayerInteractEvent.LeftClickBlock event) {
        if (!event.getEntity().isCreative()) {
            // Also cancel client prediction, so forbidden blocks do not disappear briefly.
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onWorkstation(PlayerInteractEvent.RightClickBlock event) {
        Block block = event.getLevel().getBlockState(event.getPos()).getBlock();
        if (!isWorkstation(block)) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        if (!event.getLevel().isClientSide()) {
            event.getEntity().displayClientMessage(
                    Component.translatable("message.zombieapocalypse.crafting_disabled"), true);
        }
    }

    public static boolean isWorkstation(Block block) {
        return block == Blocks.CRAFTING_TABLE || block == Blocks.CRAFTER
                || block == Blocks.FURNACE || block == Blocks.BLAST_FURNACE
                || block == Blocks.SMOKER || block == Blocks.STONECUTTER
                || block == Blocks.SMITHING_TABLE || block == Blocks.BREWING_STAND
                || block == Blocks.ENCHANTING_TABLE || block == Blocks.GRINDSTONE
                || block == Blocks.LOOM || block == Blocks.CARTOGRAPHY_TABLE
                || block instanceof AnvilBlock;
    }

    @SubscribeEvent
    public static void onMenuOpened(PlayerContainerEvent.Open event) {
        // Also cover menus opened by commands/plugins, without a block interaction.
        if (event.getEntity() instanceof ServerPlayer player && isManufacturingMenu(event.getContainer())) {
            player.closeContainer();
        }
    }

    public static boolean isManufacturingMenu(AbstractContainerMenu menu) {
        return menu instanceof CraftingMenu || menu instanceof CrafterMenu
                || menu instanceof AbstractFurnaceMenu || menu instanceof ItemCombinerMenu
                || menu instanceof StonecutterMenu || menu instanceof BrewingStandMenu
                || menu instanceof EnchantmentMenu || menu instanceof GrindstoneMenu
                || menu instanceof LoomMenu || menu instanceof CartographyTableMenu;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBrew(PotionBrewEvent.Pre event) {
        // Brewing is not backed by RecipeManager. Covers hopper-fed existing stands too.
        event.setCanceled(true);
    }
}
