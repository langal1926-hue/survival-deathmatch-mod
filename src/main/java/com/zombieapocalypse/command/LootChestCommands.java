package com.zombieapocalypse.command;

import com.mojang.brigadier.Command;
import com.zombieapocalypse.ZombieApocalypseMod;
import com.zombieapocalypse.loot.LootChestData;
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
                + " / 생성된 아이템 묶음 " + count + " / 다음 보충 60분 후"), false);
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
        LootChestData.Entry entry = LootChestData.get(source.getLevel()).lookup(pos);
        if (entry == null) return error(source, "등록되지 않은 일반/개인 상자입니다.");
        long seconds = Math.max(0, (entry.refillAt() - System.currentTimeMillis() + 999L) / 1000L);
        source.sendSuccess(() -> Component.literal("프로필: " + entry.profile().id()
                + " / 재보급까지 약 " + seconds + "초 (청크가 로드된 경우)"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int refill(CommandSourceStack source) {
        BlockPos pos = target(source);
        if (pos == null) return error(source, "상자를 바라보세요.");
        int filled = LootChestData.get(source.getLevel())
                .refillNow(source.getLevel(), pos, System.currentTimeMillis());
        if (filled < 0) return error(source, "등록된 단일 맵 상자만 즉시 재보급할 수 있습니다.");
        source.sendSuccess(() -> Component.literal("수동 재보급 완료: 빈 칸에 " + filled
                + "개 묶음 추가 / 다음 자동 재보급은 60분 후"), false);
        return Command.SINGLE_SUCCESS;
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
