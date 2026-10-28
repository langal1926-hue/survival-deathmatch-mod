package com.zombieapocalypse.survival;

import com.zombieapocalypse.item.ModItems;
import java.util.Map;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Phase 02 building goods. These mod items are sold by the future merchant;
 * during development, operators can use /give. Normal vanilla block items
 * never authorize placement, even if found in a world loot chest.
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
        var authorized = ALLOWED.get(placed);
        return authorized != null && stack.is(authorized.get());
    }
}
