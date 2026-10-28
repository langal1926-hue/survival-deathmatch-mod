package com.zombieapocalypse.rules;

import net.minecraft.core.BlockPos;
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
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.List;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("zombieapocalypse")
@PrefixGameTestTemplate(false)
public class SurvivalRulesGameTests {
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void inventoryAndWorkbenchInputsHaveNoOutputAndArePreserved(GameTestHelper helper) {
        var player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.setGameMode(GameType.SURVIVAL);
        var workbench = new CraftingMenu(1, player.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), BlockPos.ZERO));
        for (AbstractContainerMenu menu : List.of(player.inventoryMenu, workbench)) {
            int size = menu == workbench ? 3 : 2;
            var slots = List.of(1, 2, 1 + size, 2 + size);
            for (int slot : slots) {
                menu.getSlot(slot).set(new ItemStack(Items.OAK_PLANKS, 4));
            }
            check(menu.getSlot(0).getItem().isEmpty(), "No craftable result should be offered");
            menu.clicked(0, 0, ClickType.PICKUP, player);
            menu.clicked(0, 0, ClickType.QUICK_MOVE, player);
            check(menu.getCarried().isEmpty(), "Clicking the output must not produce an item");
            for (int slot : slots) {
                equal(4, menu.getSlot(slot).getItem().getCount(), "Inputs must be retained");
                menu.getSlot(slot).set(ItemStack.EMPTY);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void resourceReloadCannotRestoreRecipes(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var reload = server.reloadResources(server.getPackRepository().getSelectedIds());
        helper.succeedWhen(() -> {
            helper.assertTrue(reload.isDone(), "Waiting for resource reload");
            reload.join();
            helper.assertTrue(server.getRecipeManager().getRecipes().isEmpty(), "Reload restored recipes");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void survivalCannotBreakButCreativeCanEdit(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = FakePlayerFactory.getMinecraft(level);
        for (GameType mode : List.of(GameType.SURVIVAL, GameType.ADVENTURE, GameType.CREATIVE)) {
            player.setGameMode(mode);
            var event = new BlockEvent.BreakEvent(level, BlockPos.ZERO,
                    Blocks.STONE.defaultBlockState(), player);
            NeoForge.EVENT_BUS.post(event);
            equal(mode != GameType.CREATIVE, event.isCanceled(), mode.toString());
        }
        player.setGameMode(GameType.SURVIVAL);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void lootingAndPlayerInventoryRemainAccessible(GameTestHelper helper) {
        var player = FakePlayerFactory.getMinecraft(helper.getLevel());
        reject(SurvivalRestrictions.isManufacturingMenu(player.inventoryMenu));
        reject(SurvivalRestrictions.isManufacturingMenu(ChestMenu.threeRows(1, player.getInventory())));
        check(SurvivalRestrictions.isManufacturingMenu(new CraftingMenu(1, player.getInventory())));
        for (var block : List.of(Blocks.CHEST, Blocks.BARREL, Blocks.OAK_DOOR,
                Blocks.OAK_TRAPDOOR, Blocks.OAK_FENCE_GATE)) {
            reject(SurvivalRestrictions.isWorkstation(block));
        }
        for (var block : List.of(Blocks.CRAFTING_TABLE, Blocks.CRAFTER, Blocks.FURNACE,
                Blocks.BLAST_FURNACE, Blocks.SMOKER, Blocks.STONECUTTER, Blocks.SMITHING_TABLE,
                Blocks.BREWING_STAND, Blocks.ENCHANTING_TABLE, Blocks.GRINDSTONE, Blocks.LOOM,
                Blocks.CARTOGRAPHY_TABLE, Blocks.ANVIL, Blocks.CHIPPED_ANVIL, Blocks.DAMAGED_ANVIL)) {
            check(SurvivalRestrictions.isWorkstation(block));
        }
        helper.succeed();
    }
    private static void check(boolean value, String... message) {
        if (!value) throw new AssertionError(message.length == 0 ? "Expected true" : message[0]);
    }

    private static void reject(boolean value, String... message) {
        check(!value, message);
    }

    private static void equal(Object expected, Object actual, String... message) {
        check(expected.equals(actual), message.length == 0 ? "Expected " + expected + ", got " + actual : message[0]);
    }
}
