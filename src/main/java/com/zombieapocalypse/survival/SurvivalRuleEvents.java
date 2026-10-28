package com.zombieapocalypse.survival;

import com.zombieapocalypse.ZombieApocalypseMod;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.CartographyTableBlock;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.EnchantmentTableBlock;
import net.minecraft.world.level.block.GrindstoneBlock;
import net.minecraft.world.level.block.LoomBlock;
import net.minecraft.world.level.block.SmithingTableBlock;
import net.minecraft.world.level.block.StonecutterBlock;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * Phase 02 server-authoritative player terrain protection and building rules.
 * Existing buildings are not marked as player builds and cannot be mined.
 * Only authorized construction goods can place blocks; owners can dismantle
 * their own builds. Later teams can extend the ownership policy.
 */
@EventBusSubscriber(modid = ZombieApocalypseMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class SurvivalRuleEvents {
    private static final Component NO_MINING = Component.literal(
            "맵의 블록은 채굴할 수 없습니다. 자신이 설치한 건축물만 철거할 수 있습니다.");
    private static final Component NO_BUILDING = Component.literal(
            "전용 건축자재만 설치할 수 있습니다. 건축자재는 상인에게 구매해야 합니다.");
    private static final Component NO_CRAFTING = Component.literal(
            "이 서버에서는 제작 시설을 사용할 수 없습니다. 아이템은 파밍과 거래로 획득하세요.");

    private SurvivalRuleEvents() {}

    private static boolean isAdminBuilder(ServerPlayer player) {
        return player.isCreative() && player.hasPermissions(2);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)
                || !(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }

        ConstructionLedger ledger = ConstructionLedger.get(level);
        if (isAdminBuilder(player)) {
            // Avoid stale ownership when admins edit buildings by hand.
            ledger.forget(event.getPos());
            return;
        }

        UUID owner = ledger.owner(event.getPos());
        if (owner == null || !owner.equals(player.getUUID())) {
            event.setCanceled(true);
            player.displayClientMessage(NO_MINING, true);
            return;
        }
        // Only an owner-controlled constructed block can be dismantled.
        ledger.forget(event.getPos());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (isAdminBuilder(player)) {
            // Creative operator edits form the base map, not player-owned builds.
            ConstructionLedger.get(level).forget(event.getPos());
            return;
        }

        // Multi-block modifications (doors, beds, etc.) are not supported
        // by the restricted construction catalog.
        if (event instanceof BlockEvent.EntityMultiPlaceEvent
                || !isAuthorizedPlacement(player, event.getPlacedBlock().getBlock())) {
            event.setCanceled(true);
            player.displayClientMessage(NO_BUILDING, true);
            return;
        }

        ConstructionLedger.get(level).record(event.getPos(), player.getUUID());
    }

    private static boolean isAuthorizedPlacement(ServerPlayer player, Block block) {
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        // Do not accept an unrelated authorization item in the other hand
        // if a plain vanilla BlockItem for this same block is also held.
        if (isPlainBlockFor(main, block) || isPlainBlockFor(off, block)) {
            return false;
        }
        return ConstructionMaterials.matches(main, block)
                || ConstructionMaterials.matches(off, block);
    }

    private static boolean isPlainBlockFor(ItemStack stack, Block block) {
        return stack.getItem() instanceof BlockItem item
                && item.getBlock() == block
                && !ConstructionMaterials.matches(stack, block);
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof ServerPlayer player)
                || isAdminBuilder(player)) {
            return;
        }

        Block target = level.getBlockState(event.getPos()).getBlock();
        if (isManufacturingStation(target)) {
            event.setCanceled(true);
            player.displayClientMessage(NO_CRAFTING, true);
            return;
        }

        // Prevent direct world modification without placing a BlockItem.
        ItemStack held = player.getItemInHand(event.getHand());
        if (held.getItem() instanceof BucketItem
                || held.getItem() instanceof BoneMealItem
                || held.is(Items.FLINT_AND_STEEL)
                || held.is(Items.FIRE_CHARGE)
                || held.is(Items.POWDER_SNOW_BUCKET)) {
            event.setCanceled(true);
            player.displayClientMessage(NO_BUILDING, true);
        }
        // Containers, doors, buttons and levers remain interactable.
    }

    private static boolean isManufacturingStation(Block block) {
        return block instanceof CraftingTableBlock
                || block instanceof AbstractFurnaceBlock
                || block instanceof SmithingTableBlock
                || block instanceof StonecutterBlock
                || block instanceof AnvilBlock
                || block instanceof GrindstoneBlock
                || block instanceof LoomBlock
                || block instanceof CartographyTableBlock
                || block instanceof BrewingStandBlock
                || block instanceof EnchantmentTableBlock
                || block instanceof CrafterBlock
                || block == Blocks.SMITHING_TABLE;
    }


    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || isAdminBuilder(player)) {
            return;
        }
        // Buckets can be used on fluids or in the air, bypassing RightClickBlock.
        ItemStack held = event.getItemStack();
        if (held.getItem() instanceof BucketItem
                || held.is(Items.POWDER_SNOW_BUCKET)) {
            event.setCanceled(true);
            player.displayClientMessage(NO_BUILDING, true);
        }
    }

    @SubscribeEvent
    public static void onToolModification(BlockEvent.BlockToolModificationEvent event) {
        // Shovel paths, axe stripping and hoe tilling change the existing map
        // without firing a regular break/place event.
        if (event.getPlayer() instanceof ServerPlayer player && !isAdminBuilder(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel) {
            // Keep the PvPvE blast damage, but never destroy the city map.
            event.getAffectedBlocks().clear();
        }
    }

    @SubscribeEvent
    public static void onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (event.getLevel() instanceof ServerLevel) {
            event.setCanceled(true);
        }
    }
}
