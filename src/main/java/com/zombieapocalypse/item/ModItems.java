package com.zombieapocalypse.item;

import com.zombieapocalypse.ZombieApocalypseMod;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Item registry for Zombie Apocalypse SMP. */
public final class ModItems {
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ZombieApocalypseMod.MOD_ID);

    public static final DeferredItem<NoiseEmitterItem> NOISE_EMITTER =
            ITEMS.registerItem("noise_emitter", NoiseEmitterItem::new,
                    new net.minecraft.world.item.Item.Properties().stacksTo(1));

    private ModItems() {}
}
