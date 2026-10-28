package com.zombieapocalypse.item;

import com.zombieapocalypse.ai.NoiseSystem;
import com.zombieapocalypse.entity.PistolBullet;
import com.zombieapocalypse.network.GunNetwork;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * G17 prototype: left mouse fires, R reloads (via validated C2S inputs).
 * A fast simulated bullet travels through the world instead of hitscan.
 */
public class G17PistolItem extends Item {
    private static final String LOADED_KEY = "za_g17_loaded";
    public static final int MAGAZINE_SIZE = 17;
    private static final double GUNSHOT_HEARING_RADIUS = 64.0D;
    private static final float BULLET_SPEED = 4.0F; // blocks per game tick (80 blocks/sec)
    private static final int FIRE_COOLDOWN_TICKS = 6;
    private static final int RELOAD_COOLDOWN_TICKS = 25;

    public G17PistolItem(Properties properties) {
        super(properties);
    }

    /**
     * Right mouse no longer fires. Preserve ordinary right-click use of blocks
     * while the pistol is held.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    public static void handleInput(ServerPlayer player, boolean reload) {
        // Never trust the client's held-item assertion or ammo values.
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof G17PistolItem gun)
                || player.isSpectator()
                || player.getCooldowns().isOnCooldown(gun)) {
            return;
        }

        ServerLevel level = player.serverLevel();
        if (reload) {
            gun.reload(level, player, stack);
        } else {
            gun.fire(level, player, stack);
        }
    }

    private void fire(ServerLevel level, ServerPlayer player, ItemStack stack) {
        int remaining = getLoaded(stack);
        if (remaining == 0) {
            status(player, "G17: Empty magazine! Press R to reload");
            level.playSound(null, player.blockPosition(), SoundEvents.LEVER_CLICK,
                    SoundSource.PLAYERS, 0.8F, 1.5F);
            player.getCooldowns().addCooldown(this, FIRE_COOLDOWN_TICKS);
            return;
        }

        Vec3 direction = player.getLookAngle();
        Vec3 origin = player.getEyePosition().add(direction.scale(0.45D));
        PistolBullet bullet = new PistolBullet(level, player);
        bullet.setPos(origin.x, origin.y - 0.1D, origin.z);
        boolean aiming = GunAimState.isAiming(player);
        float spreadDegrees = calculateSpread(player, aiming);
        // Physical projectile with server-authoritative random spread.
        // NeoForge/Minecraft shoot() accepts inaccuracy in degrees.
        bullet.shoot(direction.x, direction.y, direction.z, BULLET_SPEED, spreadDegrees);

        if (!level.addFreshEntity(bullet)) {
            return; // Do not consume a round when spawning failed.
        }

        setLoaded(stack, remaining - 1);
        // Recoil is a client camera effect, acknowledged only after a real shot.
        GunNetwork.sendShotFeedback(player, aiming);
        player.getCooldowns().addCooldown(this, FIRE_COOLDOWN_TICKS);

        level.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST,
                SoundSource.PLAYERS, 1.5F, 1.1F);
        level.sendParticles(ParticleTypes.CRIT, origin.x, origin.y, origin.z,
                7, 0.08D, 0.08D, 0.08D, 0.03D);
        NoiseSystem.alertNearbyInfected(level, player.position(), GUNSHOT_HEARING_RADIUS);
        status(player, "G17: " + (remaining - 1) + " / " + MAGAZINE_SIZE + " | R: Reload");
    }

    /**
     * Accuracy tiers (degrees). Standing still and ADS are most precise,
     * movement causes more spread, and jumping/sprinting is least accurate.
     */
    private static float calculateSpread(ServerPlayer player, boolean aimed) {
        if (!player.onGround()) {
            return 5.5F;
        }
        if (player.isSprinting()) {
            return 5.0F;
        }

        boolean moving = player.getDeltaMovement().horizontalDistanceSqr() > 0.0064D;
        if (aimed) {
            return moving ? 0.95F : (player.isCrouching() ? 0.18F : 0.3F);
        }
        if (moving) {
            return 3.4F;
        }
        return player.isCrouching() ? 1.6F : 2.4F;
    }

    private void reload(ServerLevel level, ServerPlayer player, ItemStack stack) {
        int loaded = getLoaded(stack);
        if (loaded >= MAGAZINE_SIZE) {
            status(player, "G17: Magazine already full");
            return;
        }
        int missing = MAGAZINE_SIZE - loaded;
        int taken = player.getAbilities().instabuild ? missing : consumeAmmo(player, missing);
        if (taken == 0) {
            status(player, "G17: No pistol ammo in inventory");
            return;
        }

        setLoaded(stack, loaded + taken);
        player.getCooldowns().addCooldown(this, RELOAD_COOLDOWN_TICKS);
        level.playSound(null, player.blockPosition(), SoundEvents.IRON_TRAPDOOR_CLOSE,
                SoundSource.PLAYERS, 0.8F, 1.5F);
        status(player, "G17: Reloaded " + (loaded + taken) + " / " + MAGAZINE_SIZE);
    }

    private static int consumeAmmo(Player player, int amount) {
        Inventory inventory = player.getInventory();
        int remaining = amount;
        for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
            ItemStack ammo = inventory.getItem(slot);
            if (!ammo.is(ModItems.PISTOL_AMMO.get())) {
                continue;
            }
            int taken = Math.min(ammo.getCount(), remaining);
            ammo.shrink(taken);
            remaining -= taken;
        }
        inventory.setChanged();
        return amount - remaining;
    }

    private static int getLoaded(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.contains(LOADED_KEY)
                ? Math.max(0, Math.min(MAGAZINE_SIZE, tag.getInt(LOADED_KEY)))
                : MAGAZINE_SIZE;
    }

    private static void setLoaded(ItemStack stack, int amount) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack,
                tag -> tag.putInt(LOADED_KEY, Math.max(0, Math.min(MAGAZINE_SIZE, amount))));
    }

    private static void status(Player player, String message) {
        player.displayClientMessage(Component.literal(message), true);
    }
}
