package com.zombieapocalypse.survival;

import com.zombieapocalypse.item.ModItems;
import java.util.Map;
import net.minecraft.world.item.BedItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BedBlock;

/**
 * Phase 02 building goods. These mod items are sold by the future merchant;
 * during development, operators can use /give. Normal vanilla block items
 * cannot authorize placement, except vanilla chests and beds sold by merchants.
 */
public final class ConstructionMaterials {
    private static final Map<Block, net.neoforged.neoforge.registries.DeferredItem<BlockItem>> ALLOWED = Map.of(
            Blocks.SPRUCE_PLANKS, ModItems.CONSTRUCTION_PLANKS,
            Blocks.COBBLESTONE, ModItems.CONSTRUCTION_COBBLESTONE,
            Blocks.STONE_BRICKS, ModItems.CONSTRUCTION_STONE_BRICKS,
            Blocks.GLASS, ModItems.CONSTRUCTION_GLASS,
            Blocks.CHEST, ModItems.CONSTRUCTION_CHEST
    );

    private ConstructionMaterials() {}

    public static boolean matches(ItemStack stack, Block placed) {
        // Merchants can sell the standard Minecraft chest and every bed color.
        // Existing mod-specific construction_chest remains valid for old worlds.
        if (placed == Blocks.CHEST && stack.is(Items.CHEST)) return true;
        if (placed instanceof BedBlock && stack.getItem() instanceof BedItem bed
                && bed.getBlock() == placed) return true;
        var authorized = ALLOWED.get(placed);
        return authorized != null && stack.is(authorized.get());
    }
}
