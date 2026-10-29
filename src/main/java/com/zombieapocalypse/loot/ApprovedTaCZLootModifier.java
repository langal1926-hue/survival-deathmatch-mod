package com.zombieapocalypse.loot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.zombieapocalypse.compat.TaCZWeaponAllowlist;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import org.jetbrains.annotations.NotNull;

/**
 * Server-side filter for vanilla / other-mod loot table results.
 *
 * TaCZ injects loot into ordinary Minecraft loot tables independently of
 * Zombie Apocalypse's /za_loot chests. Server restrictions previously removed
 * these unauthorized items only AFTER a player picked them up, leaving banned
 * sights visible in chests. Filter them before loot stacks are delivered.
 *
 * This modifier does not rewrite a world container, touch player inventories,
 * or alter approved TaCZ ammunition. It is complementary to the whitelist
 * validation in our custom LootChestFiller (which bypasses loot tables).
 */
public final class ApprovedTaCZLootModifier extends LootModifier {
    public static final MapCodec<ApprovedTaCZLootModifier> CODEC =
            RecordCodecBuilder.mapCodec(instance ->
                    codecStart(instance).apply(instance, ApprovedTaCZLootModifier::new));

    public ApprovedTaCZLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(
            ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        // Preserve other items, including all ordinary supplies and TaCZ ammo.
        generatedLoot.removeIf(stack -> !TaCZWeaponAllowlist.isAllowedInventoryStack(stack));
        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return ModLootModifiers.APPROVED_TACZ.get();
    }
}
