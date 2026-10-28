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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.entity.HumanoidArm;
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

        // Spawn the actual flying projectile at an approximate pistol muzzle,
        // not in the center of the player's head. Aim converges back to the
        // camera crosshair target so ADS and spread work as before.
        boolean aiming = GunAimState.isAiming(player);
        Vec3 look = player.getLookAngle().normalize();
        Vec3 eye = player.getEyePosition();
        Vec3 origin = calculateMuzzlePosition(player, eye, look, aiming);

        // Aim assist here changes the launch angle only. There is NO hitscan
        // damage; the bullet remains a physical entity with swept collisions.
        Vec3 end = eye.add(look.scale(80.0D));
        BlockHitResult targetHit = level.clip(new ClipContext(
                eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 target = targetHit.getType() == HitResult.Type.MISS
                ? end : targetHit.getLocation();
        // Near targets should not make the barrel shoot backwards or sideways.
        if (eye.distanceToSqr(target) < 4.0D) {
            target = eye.add(look.scale(2.0D));
        }
        Vec3 direction = target.subtract(origin).normalize();

        // If a close wall lies between the player's view and the muzzle,
        // launch on the eye side of the wall instead of from behind it.
        BlockHitResult obstruction = level.clip(new ClipContext(
                eye, origin, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (obstruction.getType() == HitResult.Type.BLOCK) {
            origin = obstruction.getLocation().add(look.scale(-0.03D));
            direction = target.subtract(origin).normalize();
        }

        PistolBullet bullet = new PistolBullet(level, player);
        bullet.setPos(origin.x, origin.y, origin.z);
        float spreadDegrees = calculateSpread(player, aiming);
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
        // A short flash / smoke puff follows the real muzzle origin.
        level.sendParticles(ParticleTypes.FLAME, origin.x, origin.y, origin.z,
                3, 0.035D, 0.035D, 0.035D, 0.015D);
        level.sendParticles(ParticleTypes.SMOKE, origin.x, origin.y, origin.z,
                2, 0.045D, 0.045D, 0.045D, 0.015D);
        NoiseSystem.alertNearbyInfected(level, player.position(), GUNSHOT_HEARING_RADIUS);
        status(player, "G17: " + (remaining - 1) + " / " + MAGAZINE_SIZE + " | R: Reload");
    }

    /**
     * Offset the physical projectile origin to the right/left-hand pistol
     * muzzle, in the shooter's view basis. ADS moves the held G17 closer to
     * center, so the muzzle follows it. Server controls this offset.
     */
    private static Vec3 calculateMuzzlePosition(ServerPlayer player,
                                                Vec3 eye, Vec3 look, boolean aiming) {
        double yaw = Math.toRadians(player.getYRot());
        Vec3 right = new Vec3(-Math.cos(yaw), 0.0D, -Math.sin(yaw));
        double handSign = player.getMainArm() == HumanoidArm.RIGHT ? 1.0D : -1.0D;
        double lateral = aiming ? 0.055D : 0.22D;
        double down = aiming ? 0.13D : 0.19D;
        return eye.add(look.scale(0.58D))
                .add(right.scale(lateral * handSign))
                .add(0.0D, -down, 0.0D);
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
        GunNetwork.sendReloadFeedback(player);
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

    /** Client HUD reads the same synchronized CUSTOM_DATA value used by firing. */
    public static int getLoaded(ItemStack stack) {
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
