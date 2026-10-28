package com.zombieapocalypse.compat;

import com.tacz.guns.init.ModBlocks;
import com.zombieapocalypse.ZombieApocalypseMod;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * TaCZ stays UNMODIFIED, but its gunsmith workbenches cannot be used.
 * The zombie-apocalypse server is loot-based, not a crafting progression.
 *
 * Four distinct table variants exist in TaCZ 1.21.1. Block interaction is
 * canceled on both client and server. This is an initial acquisition guard,
 * not the final server-wide mining/crafting rules or the 14-gun whitelist.
 */
@EventBusSubscriber(modid = ZombieApocalypseMod.MOD_ID,
        bus = EventBusSubscriber.Bus.GAME)
public final class TaCZCraftingRestrictions {
    private TaCZCraftingRestrictions() {}

    @SubscribeEvent
    public static void stopGunsmithing(PlayerInteractEvent.RightClickBlock event) {
        Block block = event.getLevel().getBlockState(event.getPos()).getBlock();
        if (block == ModBlocks.GUN_SMITH_TABLE.get()
                || block == ModBlocks.WORKBENCH_111.get()
                || block == ModBlocks.WORKBENCH_211.get()
                || block == ModBlocks.WORKBENCH_121.get()) {
            event.setCanceled(true);
        }
    }
}
