package com.zombieapocalypse.command;

import com.mojang.brigadier.Command;
import com.zombieapocalypse.ZombieApocalypseMod;
import com.zombieapocalypse.loot.LootChestData;
import com.zombieapocalypse.compat.TaCZWeaponAllowlist;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Operator-only tool: aim at a single existing map chest within 6 blocks. */
@EventBusSubscriber(modid = ZombieApocalypseMod.MOD_ID)
public final class LootChestCommands {
    private LootChestCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        var registration = Commands.literal("register");
        for (LootChestData.Profile profile : LootChestData.Profile.values()) {
            registration.then(Commands.literal(profile.id())
                    .executes(context -> registerChest(context.getSource(), profile)));
        }
        event.getDispatcher().register(Commands.literal("za_loot")
                .requires(source -> source.hasPermission(2))
                .then(registration)
                .then(Commands.literal("unregister")
                        .executes(context -> unregisterChest(context.getSource())))
                .then(Commands.literal("status")
                        .executes(context -> status(context.getSource())))
                .then(Commands.literal("refill")
                        .executes(context -> refill(context.getSource())))
                .then(Commands.literal("audit")
                        .executes(context -> audit(context.getSource())))
                .then(Commands.literal("count")
                        .executes(context -> count(context.getSource()))));
    }

    private static BlockPos target(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            return null;
        }
        HitResult hit = player.pick(6.0D, 0.0F, false);
        return hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
                ? block.getBlockPos() : null;
    }

    private static int registerChest(CommandSourceStack source, LootChestData.Profile profile) {
        BlockPos pos = target(source);
        if (pos == null) return error(source, "6블록 안의 맵 상자를 바라보세요.");
        ServerLevel level = source.getLevel();
        LootChestData data = LootChestData.get(level);
        if (!LootChestData.eligible(level, pos))
            return error(source, "등록 불가: 단일 일반 맵 상자만 가능합니다. 플레이어가 설치한 상자는 제외됩니다.");
        if (data.lookup(pos) != null) return error(source, "이미 등록된 파밍 상자입니다.");
        if (!(level.getBlockEntity(pos) instanceof ChestBlockEntity chest) || !chest.isEmpty())
            return error(source, "등록할 상자를 먼저 비워야 합니다. 기존 아이템을 보존하기 위한 규칙입니다.");
        int count = data.register(level, pos, profile, System.currentTimeMillis());
        if (count < 0) return error(source, "파밍 상자 등록에 실패했습니다.");
        source.sendSuccess(() -> Component.literal("파밍 상자 등록 완료: " + profile.id()
                + " / 생성된 아이템 묶음 " + count + " / 모든 아이템을 꺼내면 60분 카운트다운 시작"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int unregisterChest(CommandSourceStack source) {
        BlockPos pos = target(source);
        if (pos == null || !LootChestData.get(source.getLevel()).unregister(pos))
            return error(source, "바라보는 상자가 등록되어 있지 않습니다.");
        source.sendSuccess(() -> Component.literal("파밍 상자 지정만 해제했습니다. 아이템은 그대로 유지됩니다."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int status(CommandSourceStack source) {
        BlockPos pos = target(source);
        if (pos == null) return error(source, "상자를 바라보세요.");
        ServerLevel level = source.getLevel();
        long now = System.currentTimeMillis();
        LootChestData.Entry entry = LootChestData.get(level).observe(level, pos, now);
        if (entry == null) return error(source, "등록되지 않은 일반/개인 상자입니다.");

        String message;
        if (entry.waitingForEmpty()) {
            message = "상자에 아이템이 남아 있습니다. 완전히 비우면 60분 타이머가 시작됩니다.";
        } else {
            long seconds = Math.max(0L, (entry.refillAt() - now + 999L) / 1000L);
            message = "상자가 비었습니다. 재보급까지 약 " + seconds + "초";
        }
        source.sendSuccess(() -> Component.literal("프로필: " + entry.profile().id()
                + " / " + message), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int refill(CommandSourceStack source) {
        BlockPos pos = target(source);
        if (pos == null) return error(source, "상자를 바라보세요.");
        int filled = LootChestData.get(source.getLevel())
                .refillNow(source.getLevel(), pos, System.currentTimeMillis());
        if (filled == -2) return error(source, "상자를 완전히 비워야 수동 재보급할 수 있습니다.");
        if (filled < 0) return error(source, "등록된 단일 맵 상자만 즉시 재보급할 수 있습니다.");
        source.sendSuccess(() -> Component.literal("수동 재보급 완료: " + filled
                + "개 묶음 생성 / 다시 모두 비우면 60분 타이머 시작"), false);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Read-only operator diagnosis. Works on an ordinary chest whether or not
     * it was registered as a Zombie Apocalypse map-loot chest. This identifies
     * TaCZ scopes added by other loot sources and shows the exact matching gun.
     */
    private static int audit(CommandSourceStack source) {
        BlockPos pos = target(source);
        if (pos == null || !(source.getLevel().getBlockEntity(pos) instanceof ChestBlockEntity chest)) {
            return error(source, "6블록 안의 일반 상자를 바라보세요.");
        }

        int checked = 0;
        int invalid = 0;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack stack = chest.getItem(slot);
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof IAttachment attachment) {
                checked++;
                ResourceLocation id = attachment.getAttachmentId(stack);
                boolean allowed = TaCZWeaponAllowlist.isAllowedInventoryStack(stack);
                if (!allowed) invalid++;

                List<String> compatible = new ArrayList<>();
                if (id != null) {
                    for (ResourceLocation gunId : TaCZWeaponAllowlist.approvedGunIds()) {
                        var loadout = TaCZWeaponAllowlist.approvedLoadout(gunId);
                        if (id.equals(loadout.scope()) || id.equals(loadout.muzzle())) {
                            compatible.add(gunId.getPath());
                        }
                    }
                }
                compatible.sort(String::compareTo);
                final int displayedSlot = slot;
                String message = "슬롯 " + displayedSlot + " / " + id
                        + " / " + (allowed ? "서버 허용" : "서버 금지")
                        + " / 호환 총기: " + (compatible.isEmpty() ? "없음" : String.join(", ", compatible));
                source.sendSuccess(() -> Component.literal(message), false);
            } else if (stack.getItem() instanceof IGun) {
                checked++;
                if (!TaCZWeaponAllowlist.isAllowedInventoryStack(stack)) {
                    invalid++;
                    ResourceLocation id = ((IGun) stack.getItem()).getGunId(stack);
                    source.sendSuccess(() -> Component.literal(
                            "서버에서 허용되지 않는 총기 발견: " + id), false);
                }
            }
        }
        final int total = checked;
        final int banned = invalid;
        source.sendSuccess(() -> Component.literal(
                "상자 TaCZ 검사 완료: " + total + "개 확인, 금지 품목 " + banned
                        + "개. 이 명령어는 아이템을 변경하지 않습니다."), false);
        return 1;
    }

    private static int count(CommandSourceStack source) {
        int count = LootChestData.get(source.getLevel()).size();
        source.sendSuccess(() -> Component.literal("이 차원에 등록된 파밍 상자: " + count + "개"), false);
        return count == 0 ? Command.SINGLE_SUCCESS : count;
    }

    private static int error(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal(message));
        return 0;
    }
}
