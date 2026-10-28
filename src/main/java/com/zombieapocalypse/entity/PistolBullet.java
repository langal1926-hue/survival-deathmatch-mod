package com.zombieapocalypse.entity;

import com.zombieapocalypse.item.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Fast physical pistol bullet. Vanilla projectile movement performs swept
 * collision checks each tick, so entities and blocks can intercept the shot.
 */
public class PistolBullet extends ThrowableItemProjectile {
    private static final float DAMAGE = 7.0F;
    private static final int MAX_AGE_TICKS = 22;

    public PistolBullet(EntityType<? extends PistolBullet> type, Level level) {
        super(type, level);
    }

    public PistolBullet(Level level, LivingEntity owner) {
        super(ModEntities.PISTOL_BULLET.get(), owner, level);
        this.setNoGravity(true);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.PISTOL_AMMO.get();
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide() && this.tickCount >= MAX_AGE_TICKS) {
            this.discard();
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        Entity target = result.getEntity();
        Entity owner = this.getOwner();
        if (owner instanceof LivingEntity shooter) {
            target.hurt(this.damageSources().mobProjectile(this, shooter), DAMAGE);
        } else {
            target.hurt(this.damageSources().thrown(this, owner), DAMAGE);
        }
        level.sendParticles(ParticleTypes.CRIT, this.getX(), this.getY(), this.getZ(),
                5, 0.12D, 0.12D, 0.12D, 0.06D);
        this.discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (!this.level().isClientSide()) {
            this.discard();
        }
    }
}
