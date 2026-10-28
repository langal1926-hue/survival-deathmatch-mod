package com.zombieapocalypse.item;

import com.zombieapocalypse.ai.NoiseSystem;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * First gameplay-only pistol prototype.
 *
 * Right-click fires a server-authoritative raycast. Sneak + right-click reloads
 * from the player's pistol ammo stacks. Each gun stores its own magazine count
 * in the vanilla CUSTOM_DATA item component, so it persists in inventories.
 *
 * This uses a placeholder item icon and sounds, not a 3-D firearm model.
 */
public class G17PistolItem extends Item {
    private static final String LOADED_KEY = "za_g17_loaded";
    public static final int MAGAZINE_SIZE = 17;
    private static final double RANGE = 48.0D;
    private static final float DAMAGE = 7.0F;
    private static final double GUNSHOT_HEARING_RADIUS = 64.0D;
    private static final int FIRE_COOLDOWN_TICKS = 6;
    private static final int RELOAD_COOLDOWN_TICKS = 25;

    public G17PistolItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack pistol = player.getItemInHand(hand);

        // The client must not change ammo, apply damage, or emit zombie noise.
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.sidedSuccess(pistol, true);
        }

        if (player.isCrouching()) {
            reload(serverLevel, player, pistol);
        } else {
            fire(serverLevel, player, pistol);
        }
        return InteractionResultHolder.sidedSuccess(pistol, false);
    }

    private void fire(ServerLevel level, Player player, ItemStack pistol) {
        int loaded = getLoaded(pistol);
        if (loaded <= 0) {
            status(player, "Empty! Sneak + right-click to reload");
            level.playSound(null, player.blockPosition(), SoundEvents.LEVER_CLICK,
                    SoundSource.PLAYERS, 0.8F, 1.4F);
            player.getCooldowns().addCooldown(this, 6);
            return;
        }

        setLoaded(pistol, loaded - 1);
        player.getCooldowns().addCooldown(this, FIRE_COOLDOWN_TICKS);

        // A projectile-like shot for gameplay purposes. Blocks stop the ray.
        Vec3 from = player.getEyePosition();
        Vec3 direction = player.getLookAngle();
        Vec3 maximumEnd = from.add(direction.scale(RANGE));
        BlockHitResult blockHit = level.clip(new ClipContext(
                from, maximumEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 end = blockHit.getType() == HitResult.Type.MISS ? maximumEnd : blockHit.getLocation();
        hitFirstLivingEntity(level, player, from, end);

        level.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST,
                SoundSource.PLAYERS, 1.5F, 1.1F);
        level.sendParticles(ParticleTypes.CRIT,
                from.x + direction.x, from.y + direction.y, from.z + direction.z,
                6, 0.08D, 0.08D, 0.08D, 0.02D);

        // Reuse exactly the same hearing API as alarms and other game actions.
        NoiseSystem.alertNearbyInfected(level, player.position(), GUNSHOT_HEARING_RADIUS);
        status(player, "G17  " + (loaded - 1) + " / " + MAGAZINE_SIZE + "  |  Sneak + right-click: reload");
    }

    private void hitFirstLivingEntity(ServerLevel level, Player shooter, Vec3 from, Vec3 end) {
        AABB box = new AABB(from, end).inflate(0.8D);
        LivingEntity closest = null;
        Vec3 hitPosition = null;
        double nearestDistance = from.distanceToSqr(end);

        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != shooter && e.isAlive() && !e.isSpectator() && e.isPickable())) {
            Optional<Vec3> intersection = candidate.getBoundingBox().inflate(0.18D).clip(from, end);
            if (intersection.isEmpty()) {
                continue;
            }
            double distance = from.distanceToSqr(intersection.get());
            if (distance <= nearestDistance) {
                nearestDistance = distance;
                closest = candidate;
                hitPosition = intersection.get();
            }
        }

        if (closest != null && hitPosition != null) {
            // Prototype player-attributed damage. A custom gunshot damage type
            // and armor tuning will be added when the combat system is expanded.
            closest.hurt(level.damageSources().playerAttack(shooter), DAMAGE);
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, hitPosition.x, hitPosition.y,
                    hitPosition.z, 5, 0.13D, 0.13D, 0.13D, 0.08D);
        }
    }

    private void reload(ServerLevel level, Player player, ItemStack pistol) {
        int loaded = getLoaded(pistol);
        if (loaded >= MAGAZINE_SIZE) {
            status(player, "Magazine already full: " + loaded + " / " + MAGAZINE_SIZE);
            return;
        }

        int missing = MAGAZINE_SIZE - loaded;
        int taken = player.getAbilities().instabuild ? missing : consumeAmmo(player, missing);

        if (taken <= 0) {
            status(player, "No pistol ammo! Get zombieapocalypse:pistol_ammo");
            return;
        }

        setLoaded(pistol, loaded + taken);
        player.getCooldowns().addCooldown(this, RELOAD_COOLDOWN_TICKS);
        level.playSound(null, player.blockPosition(), SoundEvents.IRON_TRAPDOOR_CLOSE,
                SoundSource.PLAYERS, 0.8F, 1.5F);
        status(player, "Reloaded G17: " + (loaded + taken) + " / " + MAGAZINE_SIZE);
    }

    private static int consumeAmmo(Player player, int amount) {
        int remaining = amount;
        Inventory inventory = player.getInventory();

        for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
            ItemStack ammo = inventory.getItem(slot);
            if (!ammo.is(ModItems.PISTOL_AMMO.get())) {
                continue;
            }
            int consumed = Math.min(ammo.getCount(), remaining);
            ammo.shrink(consumed);
            remaining -= consumed;
        }
        inventory.setChanged();
        return amount - remaining;
    }

    private static int getLoaded(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        // Newly obtained pistols start loaded; zero is a valid saved value.
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
