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


    // Phase 02: merchant-supplied building materials. Ordinary vanilla block
    // items cannot bypass the server's world-protection placement rules.
    public static final DeferredItem<net.minecraft.world.item.BlockItem> CONSTRUCTION_PLANKS =
            ITEMS.registerItem("construction_planks",
                    props -> new net.minecraft.world.item.BlockItem(
                            net.minecraft.world.level.block.Blocks.SPRUCE_PLANKS, props),
                    new Item.Properties().stacksTo(64));
    public static final DeferredItem<net.minecraft.world.item.BlockItem> CONSTRUCTION_COBBLESTONE =
            ITEMS.registerItem("construction_cobblestone",
                    props -> new net.minecraft.world.item.BlockItem(
                            net.minecraft.world.level.block.Blocks.COBBLESTONE, props),
                    new Item.Properties().stacksTo(64));
    public static final DeferredItem<net.minecraft.world.item.BlockItem> CONSTRUCTION_STONE_BRICKS =
            ITEMS.registerItem("construction_stone_bricks",
                    props -> new net.minecraft.world.item.BlockItem(
                            net.minecraft.world.level.block.Blocks.STONE_BRICKS, props),
                    new Item.Properties().stacksTo(64));
    public static final DeferredItem<net.minecraft.world.item.BlockItem> CONSTRUCTION_GLASS =
            ITEMS.registerItem("construction_glass",
                    props -> new net.minecraft.world.item.BlockItem(
                            net.minecraft.world.level.block.Blocks.GLASS, props),
                    new Item.Properties().stacksTo(64));
    public static final DeferredItem<net.minecraft.world.item.BlockItem> CONSTRUCTION_CHEST =
            ITEMS.registerItem("construction_chest",
                    props -> new net.minecraft.world.item.BlockItem(
                            net.minecraft.world.level.block.Blocks.CHEST, props),
                    new Item.Properties().stacksTo(64));

    private ModItems() {}
}
