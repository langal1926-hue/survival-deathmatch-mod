package com.zombieapocalypse.entity;

import java.util.EnumSet;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A tougher infected that investigates reported noises.
 * The noise system will later receive events from guns and voice chat.
 */
public class InfectedZombie extends Zombie {
    private static final int NOISE_MEMORY_TICKS = 20 * 12;
    private static final double ARRIVAL_DISTANCE_SQUARED = 3.0D * 3.0D;

    private Vec3 noisePosition;
    private int noiseMemoryTicks;

    public InfectedZombie(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        // Combat has a higher priority than investigating a sound.
        this.goalSelector.addGoal(3, new InvestigateNoiseGoal(this));
    }

    /**
     * Called only on the logical server.
     * Investigating never replaces an active hostile target.
     */
    public boolean hearNoise(Vec3 position) {
        if (this.getTarget() != null || !this.isAlive()) {
            return false;
        }
        this.noisePosition = position;
        this.noiseMemoryTicks = NOISE_MEMORY_TICKS;
        return true;
    }

    public static AttributeSupplier.Builder createInfectedAttributes() {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 28.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.27D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D);
    }

    private static final class InvestigateNoiseGoal extends Goal {
        private final InfectedZombie infected;

        private InvestigateNoiseGoal(InfectedZombie infected) {
            this.infected = infected;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return this.infected.noisePosition != null
                    && this.infected.noiseMemoryTicks > 0
                    && this.infected.getTarget() == null;
        }

        @Override
        public boolean canContinueToUse() {
            return this.canUse()
                    && this.infected.distanceToSqr(this.infected.noisePosition) > ARRIVAL_DISTANCE_SQUARED;
        }

        @Override
        public void start() {
            this.moveTowardsNoise();
        }

        @Override
        public void tick() {
            this.infected.noiseMemoryTicks--;
            if (this.infected.tickCount % 20 == 0 && this.infected.getNavigation().isDone()) {
                this.moveTowardsNoise();
            }
        }

        private void moveTowardsNoise() {
            Vec3 target = this.infected.noisePosition;
            if (target != null) {
                this.infected.getNavigation().moveTo(target.x, target.y, target.z, 1.15D);
            }
        }

        @Override
        public void stop() {
            this.infected.noisePosition = null;
            this.infected.noiseMemoryTicks = 0;
        }
    }
}
