package com.zombieapocalypse.loot;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.zombieapocalypse.ZombieApocalypseMod;
import com.zombieapocalypse.compat.TaCZWeaponAllowlist;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/**
 * Small first-pass loot pools, with all TaCZ gun IDs validated against the
 * server's 14-gun allowlist. No unapproved gun or attachment can be spawned.
 * The 60-minute scheduler only adds to vacant slots: it NEVER deletes items.
 */
public final class LootChestFiller {
    private record Drop(int weight, Supplier<ItemStack> item) {}
    private LootChestFiller() {}

    public static int fill(ServerLevel level, ChestBlockEntity chest, LootChestData.Profile profile) {
        RandomSource random = level.getRandom();
        List<Drop> pool = pool(level, profile, random);
        List<Integer> emptySlots = new ArrayList<>();
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            if (chest.getItem(slot).isEmpty()) emptySlots.add(slot);
        }
        if (emptySlots.isEmpty()) return 0;
        Collections.shuffle(emptySlots, new java.util.Random(random.nextLong()));
        int rolls = 3 + random.nextInt(4);  // Three to six stacks per cycle.
        int inserted = 0;
        for (int i = 0; i < Math.min(rolls, emptySlots.size()); i++) {
            ItemStack stack = pick(pool, random);
            if (stack.isEmpty()) continue;
            chest.setItem(emptySlots.get(i), stack);
            inserted++;
        }
        if (inserted > 0) chest.setChanged();
        return inserted;
    }

    private static ItemStack pick(List<Drop> pool, RandomSource random) {
        int total = 0;
        for (Drop drop : pool) total += drop.weight();
        int roll = random.nextInt(total);
        for (Drop drop : pool) {
            roll -= drop.weight();
            if (roll < 0) return drop.item().get();
        }
        return ItemStack.EMPTY;
    }

    private static List<Drop> pool(ServerLevel level, LootChestData.Profile profile, RandomSource random) {
        List<Drop> drops = new ArrayList<>();
        switch (profile) {
            case CIVILIAN -> {
                add(drops, 28, () -> vanilla(Items.BREAD, random, 1, 4));
                add(drops, 18, () -> vanilla(Items.APPLE, random, 1, 3));
                add(drops, 12, () -> vanilla(Items.HONEY_BOTTLE, random, 1, 2));
                add(drops, 12, () -> vanilla(Items.GOLD_NUGGET, random, 2, 7)); // Future currency
                add(drops, 10, () -> ammo("tacz:glock_17", random, 4, 13));
                gun(drops, 6, level, "tacz:glock_17");
                gun(drops, 5, level, "tacz:m1911");
                gun(drops, 1, level, "tacz:deagle");
                gun(drops, 3, level, "tacz:db_short");
                gun(drops, 3, level, "tacz:m870");
                gun(drops, 2, level, "tacz:m1014");
            }
            case HOSPITAL -> {
                add(drops, 33, () -> vanilla(Items.HONEY_BOTTLE, random, 1, 3));
                add(drops, 24, () -> vanilla(Items.POTION, random, 1, 2)); // Water bottle placeholder
                add(drops, 22, () -> vanilla(Items.GOLDEN_CARROT, random, 1, 3));
                add(drops, 15, () -> vanilla(Items.BREAD, random, 1, 3));
                add(drops, 6, () -> vanilla(Items.GOLDEN_APPLE, random, 1, 1));
            }
            case POLICE -> {
                add(drops, 24, () -> ammo("tacz:glock_17", random, 8, 20));
                add(drops, 22, () -> ammo("tacz:hk_mp5a5", random, 8, 20));
                add(drops, 18, () -> vanilla(Items.GOLD_NUGGET, random, 2, 7));
                add(drops, 12, () -> vanilla(Items.COOKED_BEEF, random, 1, 3));
                gun(drops, 9, level, "tacz:glock_17");
                gun(drops, 5, level, "tacz:m1911");
                gun(drops, 6, level, "tacz:hk_mp5a5");
                gun(drops, 4, level, "tacz:ump45");
            }
            case MILITARY -> {
                add(drops, 32, () -> ammo("tacz:m4a1", random, 12, 28));
                add(drops, 16, () -> ammo("tacz:m700", random, 5, 12));
                add(drops, 12, () -> vanilla(Items.GOLD_INGOT, random, 1, 3));
                add(drops, 10, () -> vanilla(Items.COOKED_BEEF, random, 2, 5));
                gun(drops, 7, level, "tacz:ak47");
                gun(drops, 7, level, "tacz:m4a1");
                gun(drops, 6, level, "tacz:aug");
                gun(drops, 5, level, "tacz:scar_h");
                gun(drops, 3, level, "tacz:m700");
                gun(drops, 2, level, "tacz:ai_awp");
            }
        }
        return drops;
    }

    private static void gun(List<Drop> drops, int weight, ServerLevel level, String id) {
        ResourceLocation gunId = ResourceLocation.parse(id);
        // Crash early for developer mistakes, rather than dropping a banned gun.
        if (!TaCZWeaponAllowlist.isAllowedGunId(gunId)) {
            throw new IllegalStateException("Forbidden gun in loot table: " + gunId);
        }
        add(drops, weight, () -> TimelessAPI.getCommonGunIndex(gunId)
                .map(index -> GunItemBuilder.create()
                        .setId(gunId)
                        .setFireMode(index.getGunData().getFireModeSet().get(0))
                        .setAmmoCount(0)
                        .setAmmoInBarrel(false)
                        .setHeatData(index.getGunData().hasHeatData())
                        .build(level.registryAccess()))
                .orElseGet(() -> {
                    ZombieApocalypseMod.LOGGER.warn("TaCZ gun missing from active pack: {}", gunId);
                    return ItemStack.EMPTY;
                }));
    }

    private static ItemStack ammo(String gun, RandomSource random, int min, int max) {
        return TimelessAPI.getCommonGunIndex(ResourceLocation.parse(gun))
                .map(index -> AmmoItemBuilder.create()
                        .setId(index.getGunData().getAmmoId())
                        .setCount(min + random.nextInt(max - min + 1))
                        .build())
                .orElse(ItemStack.EMPTY);
    }

    private static ItemStack vanilla(Item item, RandomSource random, int min, int max) {
        return new ItemStack(item, min + random.nextInt(max - min + 1));
    }

    private static void add(List<Drop> drops, int weight, Supplier<ItemStack> stack) {
        drops.add(new Drop(weight, stack));
    }
}
