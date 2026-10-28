package com.zombieapocalypse.command;

import com.mojang.brigadier.Command;
import com.zombieapocalypse.ZombieApocalypseMod;
import com.zombieapocalypse.ai.NoiseSystem;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Temporary, operator-only manual sound test.
 * Run /za_noise in a cheats-enabled test world to attract nearby infected.
 */
@EventBusSubscriber(modid = ZombieApocalypseMod.MOD_ID)
public final class NoiseTestCommand {
    private static final double HEARING_RADIUS = 24.0D;

    private NoiseTestCommand() {}

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("za_noise")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    ServerLevel level = context.getSource().getLevel();
                    Vec3 position = context.getSource().getPosition();
                    BlockPos soundBlock = BlockPos.containing(position);
                    level.playSound(null, soundBlock, SoundEvents.ANVIL_LAND,
                            SoundSource.BLOCKS, 2.0F, 1.0F);

                    int count = NoiseSystem.alertNearbyInfected(level, position, HEARING_RADIUS);
                    context.getSource().sendSuccess(
                            () -> Component.literal("Noise pulse: alerted " + count + " infected (24 blocks)."),
                            false);
                    return count > 0 ? count : Command.SINGLE_SUCCESS;
                }));
    }
}
