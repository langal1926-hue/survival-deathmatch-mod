package com.zombieapocalypse.item;

import com.zombieapocalypse.ai.NoiseSystem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A reusable, handheld test alarm for the zombie hearing system.
 *
 * This is NOT a firearm. Later, a real gun can call NoiseSystem after a shot.
 */
public class NoiseEmitterItem extends Item {
    private static final double HEARING_RADIUS = 40.0D;
    private static final int COOLDOWN_TICKS = 60;

    public NoiseEmitterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel serverLevel) {
            // The sound is heard by nearby players, while AI hearing is
            // explicitly notified through the shared server-side noise system.
            serverLevel.playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND,
                    SoundSource.PLAYERS, 2.0F, 0.7F);
            serverLevel.sendParticles(ParticleTypes.NOTE,
                    player.getX(), player.getY() + 1.5D, player.getZ(),
                    12, 0.55D, 0.4D, 0.55D, 0.15D);
            NoiseSystem.alertNearbyInfected(serverLevel, player.position(), HEARING_RADIUS);
            player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
