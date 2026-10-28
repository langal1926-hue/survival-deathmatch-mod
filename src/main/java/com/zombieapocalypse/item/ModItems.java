package com.zombieapocalypse.item;

import com.zombieapocalypse.ZombieApocalypseMod;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Item registry for Zombie Apocalypse SMP. */
public final class ModItems {
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ZombieApocalypseMod.MOD_ID);

    public static final DeferredItem<NoiseEmitterItem> NOISE_EMITTER =
            ITEMS.registerItem("noise_emitter", NoiseEmitterItem::new,
                    new Item.Properties().stacksTo(1));

    public static final DeferredItem<G17PistolItem> G17_PISTOL =
            ITEMS.registerItem("g17_pistol", G17PistolItem::new,
                    new Item.Properties().stacksTo(1));

    public static final DeferredItem<Item> PISTOL_AMMO =
            ITEMS.registerSimpleItem("pistol_ammo", new Item.Properties().stacksTo(64));

    private ModItems() {}
}
