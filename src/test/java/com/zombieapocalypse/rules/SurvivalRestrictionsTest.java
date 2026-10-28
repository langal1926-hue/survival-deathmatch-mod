package com.zombieapocalypse.rules;

import net.minecraft.core.NonNullList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.brewing.PotionBrewEvent;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

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

}
