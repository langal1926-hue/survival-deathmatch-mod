package com.zombieapocalypse.loot;

import com.mojang.serialization.MapCodec;
import com.zombieapocalypse.ZombieApocalypseMod;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Registered via the mod event bus, not the gameplay event bus. */
public final class ModLootModifiers {
    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS,
                    ZombieApocalypseMod.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>,
            MapCodec<ApprovedTaCZLootModifier>> APPROVED_TACZ =
            SERIALIZERS.register("approved_tacz", () -> ApprovedTaCZLootModifier.CODEC);

    private ModLootModifiers() {}
}
