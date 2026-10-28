package com.zombieapocalypse.mixin;

import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.Map;

/**
 * Empty recipe data on every load/reload, before recipes are indexed and synced.
 * Covers inventory/table crafting, the redstone crafter, cooking, stonecutting
 * and smithing without consuming inputs or relying on a client-side GUI lock.
 * Creative operators use their inventory or /give to supply the test world.
 */
@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {
    @ModifyVariable(
            method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At("HEAD"), argsOnly = true)
    private Map<ResourceLocation, JsonElement> zombieapocalypse$disableRecipes(
            Map<ResourceLocation, JsonElement> recipes) {
        return Map.of();
    }
}
