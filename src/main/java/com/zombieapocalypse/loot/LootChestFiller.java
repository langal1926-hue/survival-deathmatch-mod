package com.zombieapocalypse.loot;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.zombieapocalypse.ZombieApocalypseMod;
import com.zombieapocalypse.compat.TaCZWeaponAllowlist;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
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
 * Phase 03 loot: exact firearm probabilities; independently rolled compatible
 * optics/suppressors; all approved distinct ammo calibers selected uniformly.
 * Never overwrites items or grants an unapproved gun or accessory.
 */
public final class LootChestFiller {
    private record Drop(int weight, Supplier<ItemStack> item) {}
    private LootChestFiller() {}

    // Exactly one category roll per newly generated or refilled map chest.
    // Values are per 1000 (e.g. 3 = 0.3%); all five classes are mutually
    // exclusive. The remainder means no firearm in this refill cycle.
    private enum GunClass { PISTOL, SHOTGUN, SMG, RIFLE, SNIPER }

    private record GunOdds(int pistol, int shotgun, int smg, int rifle, int sniper) {}

    private static GunOdds odds(LootChestData.Profile profile) {
        return switch (profile) {
            case CIVILIAN, HOSPITAL -> new GunOdds(30, 20, 10, 0, 3);
            case POLICE -> new GunOdds(200, 150, 100, 0, 5);
            case MILITARY -> new GunOdds(70, 100, 100, 270, 100);
        };
    }

    /**
     * Sample one of 1000 equally likely outcomes. Nonzero odds represent the
     * EXACT probability for a weapon class per chest refill, not per loot slot.
     * A successful result can create at most one gun.
     */
    private static GunClass rollGunClass(LootChestData.Profile profile, RandomSource random) {
        GunOdds o = odds(profile);
        int roll = random.nextInt(1000);
        if (roll < o.pistol()) return GunClass.PISTOL;
        roll -= o.pistol();
        if (roll < o.shotgun()) return GunClass.SHOTGUN;
        roll -= o.shotgun();
        if (roll < o.smg()) return GunClass.SMG;
        roll -= o.smg();
        if (roll < o.rifle()) return GunClass.RIFLE;
        roll -= o.rifle();
        if (roll < o.sniper()) return GunClass.SNIPER;
        return null;
    }

    public static int fill(ServerLevel level, ChestBlockEntity chest, LootChestData.Profile profile) {
        RandomSource random = level.getRandom();
        List<Integer> emptySlots = new ArrayList<>();
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            if (chest.getItem(slot).isEmpty()) emptySlots.add(slot);
        }
        if (emptySlots.isEmpty()) return 0;

        Collections.shuffle(emptySlots, new java.util.Random(random.nextLong()));
        int maxStacks = Math.min(3 + random.nextInt(4), emptySlots.size());
        int slotIndex = 0;
        int inserted = 0;

        // One firearm-category roll per chest, preserving established exact
        // percentages. A single refill generates at most one firearm.
        GunClass selected = rollGunClass(profile, random);
        if (selected != null) {
            ItemStack gun = makeGun(level, selected, random);
            if (!gun.isEmpty()) {
                chest.setItem(emptySlots.get(slotIndex++), gun);
                inserted++;
            }
        }

        // Independent accessory-category roll using the SAME regional odds
        // as firearms. Every outcome must belong to an approved gun in the
        // rolled class; no forbidden scopes, suppressors or hidden optics.
        GunClass accessoryClass = rollGunClass(profile, random);
        if (accessoryClass != null && slotIndex < maxStacks) {
            ItemStack accessory = makeAccessory(accessoryClass, random);
            if (!accessory.isEmpty()) {
                chest.setItem(emptySlots.get(slotIndex++), accessory);
                inserted++;
            }
        }

        List<Drop> supplies = supplyPool(profile, random);
        while (slotIndex < maxStacks) {
            ItemStack stack = pick(supplies, random);
            if (!stack.isEmpty()) {
                chest.setItem(emptySlots.get(slotIndex), stack);
                inserted++;
            }
            slotIndex++;
        }
        if (inserted > 0) chest.setChanged();
        return inserted;
    }

    // The five lists must cover every approved TaCZ gun exactly once.
    private static String[] gunIds(GunClass type) {
        return switch (type) {
            case PISTOL -> new String[] {"tacz:glock_17", "tacz:m1911", "tacz:deagle"};
            case SHOTGUN -> new String[] {"tacz:db_short", "tacz:m870", "tacz:m1014"};
            case SMG -> new String[] {"tacz:hk_mp5a5", "tacz:ump45"};
            case RIFLE -> new String[] {"tacz:ak47", "tacz:m4a1", "tacz:aug", "tacz:scar_h"};
            case SNIPER -> new String[] {"tacz:m700", "tacz:ai_awp"};
        };
    }

    private static ItemStack makeGun(ServerLevel level, GunClass type, RandomSource random) {
        String[] ids = gunIds(type);
        ResourceLocation gunId = ResourceLocation.parse(ids[random.nextInt(ids.length)]);
        if (!TaCZWeaponAllowlist.isAllowedGunId(gunId)) {
            throw new IllegalStateException("Forbidden firearm in map chest: " + gunId);
        }
        return TimelessAPI.getCommonGunIndex(gunId)
                .map(index -> GunItemBuilder.create()
                        .setId(gunId)
                        .setFireMode(index.getGunData().getFireModeSet().get(0))
                        .setAmmoCount(0)
                        .setAmmoInBarrel(false)
                        .setHeatData(index.getGunData().hasHeatData())
                        .build(level.registryAccess()))
                .orElseGet(() -> {
                    ZombieApocalypseMod.LOGGER.warn("Missing TaCZ gun: {}", gunId);
                    return ItemStack.EMPTY;
                });
    }

    private static ItemStack makeAccessory(GunClass type, RandomSource random) {
        // Each eligible gun gets an equal share of the category's accessory
        // probability. Some guns have one or zero compatible accessories.
        List<TaCZWeaponAllowlist.Loadout> candidates = new ArrayList<>();
        for (String gun : gunIds(type)) {
            ResourceLocation gunId = ResourceLocation.parse(gun);
            TaCZWeaponAllowlist.Loadout loadout = TaCZWeaponAllowlist.approvedLoadout(gunId);
            if (loadout == null) throw new IllegalStateException("Forbidden gun: " + gunId);
            if (loadout.scope() != null || loadout.muzzle() != null) {
                candidates.add(loadout);
            }
        }
        if (candidates.isEmpty()) return ItemStack.EMPTY;

        TaCZWeaponAllowlist.Loadout chosen = candidates.get(random.nextInt(candidates.size()));
        ResourceLocation attachmentId;
        if (chosen.scope() != null && chosen.muzzle() != null) {
            attachmentId = random.nextBoolean() ? chosen.scope() : chosen.muzzle();
        } else {
            attachmentId = chosen.scope() != null ? chosen.scope() : chosen.muzzle();
        }
        if (!TaCZWeaponAllowlist.approvedLooseAttachmentIds().contains(attachmentId)) {
            throw new IllegalStateException("Forbidden accessory in map chest: " + attachmentId);
        }
        return AttachmentItemBuilder.create().setId(attachmentId).build();
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

    private static List<Drop> supplyPool(LootChestData.Profile profile, RandomSource random) {
        List<Drop> drops = new ArrayList<>();
        // Resolve the actual ammo IDs for ALL 14 authorized TaCZ guns. Several
        // models share a caliber. Deduplicating by ammo ID gives each caliber
        // precisely the same chance on an ammo roll (not each gun model).
        List<ResourceLocation> calibers = eligibleAmmoIds();
        switch (profile) {
            case CIVILIAN -> {
                add(drops, 28, () -> vanilla(Items.BREAD, random, 1, 4));
                add(drops, 18, () -> vanilla(Items.APPLE, random, 1, 3));
                add(drops, 12, () -> vanilla(Items.HONEY_BOTTLE, random, 1, 2));
                add(drops, 12, () -> vanilla(Items.GOLD_NUGGET, random, 2, 7));
                if (!calibers.isEmpty()) add(drops, 10,
                        () -> balancedAmmo(calibers, random, 4, 13));
            }
            case HOSPITAL -> {
                add(drops, 33, () -> vanilla(Items.HONEY_BOTTLE, random, 1, 3));
                add(drops, 24, () -> vanilla(Items.POTION, random, 1, 2));
                add(drops, 22, () -> vanilla(Items.GOLDEN_CARROT, random, 1, 3));
                add(drops, 15, () -> vanilla(Items.BREAD, random, 1, 3));
                add(drops, 6, () -> vanilla(Items.GOLDEN_APPLE, random, 1, 1));
                // Hospital remains primarily a medical resource location.
                if (!calibers.isEmpty()) add(drops, 10,
                        () -> balancedAmmo(calibers, random, 4, 13));
            }
            case POLICE -> {
                if (!calibers.isEmpty()) add(drops, 46,
                        () -> balancedAmmo(calibers, random, 8, 20));
                add(drops, 18, () -> vanilla(Items.GOLD_NUGGET, random, 2, 7));
                add(drops, 12, () -> vanilla(Items.COOKED_BEEF, random, 1, 3));
            }
            case MILITARY -> {
                if (!calibers.isEmpty()) add(drops, 48,
                        () -> balancedAmmo(calibers, random, 12, 28));
                add(drops, 12, () -> vanilla(Items.GOLD_INGOT, random, 1, 3));
                add(drops, 10, () -> vanilla(Items.COOKED_BEEF, random, 2, 5));
            }
        }
        return drops;
    }

    private static List<ResourceLocation> eligibleAmmoIds() {
        // Sort for deterministic traversal and remove duplicates by actual
        // TaCZ data ID. No hardcoded caliber names or 9mm/5.56 preference.
        return TaCZWeaponAllowlist.approvedGunIds().stream()
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .map(TimelessAPI::getCommonGunIndex)
                .flatMap(Optional::stream)
                .map(index -> index.getGunData().getAmmoId())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private static ItemStack balancedAmmo(
            List<ResourceLocation> calibers, RandomSource random, int min, int max) {
        ResourceLocation selected = calibers.get(random.nextInt(calibers.size()));
        return AmmoItemBuilder.create()
                .setId(selected)
                .setCount(min + random.nextInt(max - min + 1))
                .build();
    }

    private static ItemStack vanilla(Item item, RandomSource random, int min, int max) {
        return new ItemStack(item, min + random.nextInt(max - min + 1));
    }

    private static void add(List<Drop> drops, int weight, Supplier<ItemStack> stack) {
        drops.add(new Drop(weight, stack));
    }
}
