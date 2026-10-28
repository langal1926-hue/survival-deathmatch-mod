package com.zombieapocalypse.survival;

import com.zombieapocalypse.ZombieApocalypseMod;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Disables the recipes on the SERVER, not merely by hiding client GUIs.
 * Reapplies following /reload before the recipes are synced to players.
 * This affects all loaded recipe namespaces, not just vanilla recipes.
 */
@EventBusSubscriber(modid = ZombieApocalypseMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class RecipeLockdown {
    private RecipeLockdown() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        removeProductionRecipes(event.getServer());
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        removeProductionRecipes(event.getPlayerList().getServer());
    }

    private static void removeProductionRecipes(MinecraftServer server) {
        RecipeManager manager = server.getRecipeManager();
        List<RecipeHolder<?>> allowed = manager.getRecipes().stream()
                .filter(holder -> !isProduction(holder.value().getType()))
                .toList();
        if (allowed.size() != manager.getRecipes().size()) {
            manager.replaceRecipes(allowed);
            ZombieApocalypseMod.LOGGER.info(
                    "Phase 02: removed production recipes; {} recipes remain on server.",
                    allowed.size());
        }
    }

    private static boolean isProduction(RecipeType<?> type) {
        return type == RecipeType.CRAFTING
                || type == RecipeType.SMELTING
                || type == RecipeType.BLASTING
                || type == RecipeType.SMOKING
                || type == RecipeType.CAMPFIRE_COOKING
                || type == RecipeType.STONECUTTING
                || type == RecipeType.SMITHING;
    }

    // Last-resort defense for a recipe from another mod or a stale screen.
    // The main enforcement is the server's recipe manager above.
    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!event.getEntity().isCreative()) {
            event.getCrafting().setCount(0);
        }
    }
}
