package com.zombieapocalypse.rules;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.brewing.PotionBrewEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Runs inside NeoForge, including the actual mixin-transformed recipe reload. */
@ExtendWith(EphemeralTestServerProvider.class)
class SurvivalRestrictionsTest {
    @Test
    void allLoadedRecipesAreDisabled(MinecraftServer server) {
        assertTrue(server.getRecipeManager().getRecipes().isEmpty(),
                "Recipe reload must remove vanilla, mod and datapack recipes");
        assertTrue(server.registryAccess().registries().findAny().isPresent(),
                "The test must have loaded a real Minecraft registry context");
    }

    @Test
    void inventoryAndWorkbenchInputsHaveNoOutputAndArePreserved(MinecraftServer server) {
        var player = FakePlayerFactory.getMinecraft(server.overworld());
        player.setGameMode(GameType.SURVIVAL);
        var workbench = new CraftingMenu(1, player.getInventory(),
                ContainerLevelAccess.create(server.overworld(), BlockPos.ZERO));
        for (AbstractContainerMenu menu : List.of(player.inventoryMenu, workbench)) {
            int size = menu == workbench ? 3 : 2;
            var slots = List.of(1, 2, 1 + size, 2 + size);
            for (int slot : slots) {
                menu.getSlot(slot).set(new ItemStack(Items.OAK_PLANKS, 4));
            }
            assertTrue(menu.getSlot(0).getItem().isEmpty(), "No craftable result should be offered");
            menu.clicked(0, 0, ClickType.PICKUP, player);
            menu.clicked(0, 0, ClickType.QUICK_MOVE, player);
            assertTrue(menu.getCarried().isEmpty(), "Clicking the output must not produce an item");
            for (int slot : slots) {
                assertEquals(4, menu.getSlot(slot).getItem().getCount(), "Inputs must be retained");
                menu.getSlot(slot).set(ItemStack.EMPTY);
            }
        }
    }

    @Test
    void resourceReloadCannotRestoreRecipes(MinecraftServer server) throws Exception {
        server.reloadResources(server.getPackRepository().getSelectedIds()).get(60, TimeUnit.SECONDS);
        assertTrue(server.getRecipeManager().getRecipes().isEmpty());
    }

    @Test
    void survivalCannotBreakButCreativeCanEdit(MinecraftServer server) {
        var level = server.overworld();
        var player = FakePlayerFactory.getMinecraft(level);
        for (GameType mode : List.of(GameType.SURVIVAL, GameType.ADVENTURE, GameType.CREATIVE)) {
            player.setGameMode(mode);
            var event = new BlockEvent.BreakEvent(level, BlockPos.ZERO,
                    Blocks.STONE.defaultBlockState(), player);
            NeoForge.EVENT_BUS.post(event);
            assertEquals(mode != GameType.CREATIVE, event.isCanceled(), mode.toString());
        }
        player.setGameMode(GameType.SURVIVAL);
    }

    @Test
    void automatedBrewingIsCancelledWithoutTakingItems(MinecraftServer server) {
        var slots = NonNullList.withSize(5, ItemStack.EMPTY);
        slots.set(0, new ItemStack(Items.POTION));
        slots.set(3, new ItemStack(Items.NETHER_WART, 8));
        var event = new PotionBrewEvent.Pre(slots);
        NeoForge.EVENT_BUS.post(event);
        assertTrue(event.isCanceled());
        assertEquals(8, slots.get(3).getCount());
        assertTrue(slots.get(0).is(Items.POTION));
    }

    @Test
    void lootingAndPlayerInventoryRemainAccessible(MinecraftServer server) {
        var player = FakePlayerFactory.getMinecraft(server.overworld());
        assertFalse(SurvivalRestrictions.isManufacturingMenu(player.inventoryMenu));
        assertFalse(SurvivalRestrictions.isManufacturingMenu(ChestMenu.threeRows(1, player.getInventory())));
        assertTrue(SurvivalRestrictions.isManufacturingMenu(new CraftingMenu(1, player.getInventory())));
        for (var block : List.of(Blocks.CHEST, Blocks.BARREL, Blocks.OAK_DOOR,
                Blocks.OAK_TRAPDOOR, Blocks.OAK_FENCE_GATE)) {
            assertFalse(SurvivalRestrictions.isWorkstation(block));
        }
        for (var block : List.of(Blocks.CRAFTING_TABLE, Blocks.CRAFTER, Blocks.FURNACE,
                Blocks.BLAST_FURNACE, Blocks.SMOKER, Blocks.STONECUTTER, Blocks.SMITHING_TABLE,
                Blocks.BREWING_STAND, Blocks.ENCHANTING_TABLE, Blocks.GRINDSTONE, Blocks.LOOM,
                Blocks.CARTOGRAPHY_TABLE, Blocks.ANVIL, Blocks.CHIPPED_ANVIL, Blocks.DAMAGED_ANVIL)) {
            assertTrue(SurvivalRestrictions.isWorkstation(block));
        }
    }
}
