package com.zombieapocalypse.entity;

import com.zombieapocalypse.ZombieApocalypseMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registered entity types belonging to this mod. */
public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, ZombieApocalypseMod.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<InfectedZombie>> INFECTED =
            ENTITY_TYPES.register("infected", () -> EntityType.Builder
                    .of(InfectedZombie::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .build("infected"));

    public static final DeferredHolder<EntityType<?>, EntityType<PistolBullet>> PISTOL_BULLET =
            ENTITY_TYPES.register("pistol_bullet", () -> EntityType.Builder
                    .<PistolBullet>of(PistolBullet::new, MobCategory.MISC)
                    .sized(0.13F, 0.13F)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build("pistol_bullet"));

    private ModEntities() {}
}
