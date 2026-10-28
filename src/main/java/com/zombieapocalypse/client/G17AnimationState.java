package com.zombieapocalypse.client;

import com.zombieapocalypse.item.ModItems;
import com.zombieapocalypse.item.G17PistolItem;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * Original, code-driven client-side G17 animation state machine.
 *
 * Architecture: independent equip / shoot / reload tracks, sampled through
 * smooth keyframe interpolation and blended with the existing ADS track.
 * In particular, this contains NO TaCZ animation files, extracted keyframes,
 * textures, sounds or code. It simply uses a standard state-machine approach
 * to create independent motion for our own Blockbench pistol and player arms.
 *
 * Reload is currently an instant server ammo transfer followed by a 38-tick
 * firing lockout. The visual timeline is deliberately the same 38 ticks and
 * starts only when the server confirms a successful reload.
 */
public final class G17AnimationState {
    public static final int RELOAD_TICKS = G17PistolItem.RELOAD_COOLDOWN_TICKS;
    private static final int SHOOT_TICKS = 9;
    private static final int EQUIP_TICKS = 12;

    // Original authored animation curves. Stored once to avoid allocating
    // temporary arrays for every frame rendered.
    private static final float[] RELOAD_LOWER_KEYS = {
            0.0F, 0.0F, 0.12F, 0.52F, 0.32F, 1.0F,
            0.61F, 0.88F, 0.83F, 0.35F, 1.0F, 0.0F
    };
    private static final float[] RELOAD_TILT_KEYS = {
            0.0F, 0.0F, 0.20F, 0.35F, 0.46F, 1.0F,
            0.72F, -0.28F, 1.0F, 0.0F
    };
    private static final float[] SUPPORT_REACH_KEYS = {
            0.0F, 0.0F, 0.20F, 0.25F, 0.39F, 1.0F,
            0.66F, 1.0F, 0.91F, 0.15F, 1.0F, 0.0F
    };

    private static boolean holdingLastTick;
    private static int selectedSlot = -1;
    private static int ageTicks;
    private static int shootTicks = -1;
    private static int reloadTicks = -1;
    private static int equipTicks = -1;
    private static float walkStrength;
    private static float previousWalkStrength;

    private G17AnimationState() {}

    /** Called from ClientTickEvent.Post; no server gameplay state is changed. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        boolean holding = mc.player != null
                && mc.player.getMainHandItem().is(ModItems.G17_PISTOL.get());

        if (!holding) {
            holdingLastTick = false;
            selectedSlot = -1;
            shootTicks = -1;
            reloadTicks = -1;
            equipTicks = -1;
            walkStrength = 0.0F;
            previousWalkStrength = 0.0F;
            return;
        }

        if (mc.isPaused()) {
            return;
        }

        int slot = mc.player.getInventory().selected;
        if (!holdingLastTick || selectedSlot != slot) {
            equipTicks = 0;
            shootTicks = -1;
            reloadTicks = -1;
            selectedSlot = slot;
        }
        holdingLastTick = true;
        ageTicks++;

        if (shootTicks >= 0) {
            shootTicks = shootTicks >= SHOOT_TICKS ? -1 : shootTicks + 1;
        }
        if (reloadTicks >= 0) {
            reloadTicks = reloadTicks >= RELOAD_TICKS ? -1 : reloadTicks + 1;
        }
        if (equipTicks >= 0) {
            equipTicks = equipTicks >= EQUIP_TICKS ? -1 : equipTicks + 1;
        }

        previousWalkStrength = walkStrength;
        float horizontalSpeed = (float) Math.sqrt(
                mc.player.getDeltaMovement().horizontalDistanceSqr());
        float desired = Mth.clamp((horizontalSpeed - 0.015F) * 4.2F, 0.0F, 1.0F);
        walkStrength = Mth.lerp(0.25F, walkStrength, desired);
    }

    /**
     * The server sends ShotFeedback ONLY after a physical projectile spawns.
     * Do not animate false shots (empty magazine / firing cooldown).
     */
    public static void shotConfirmed() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getMainHandItem().is(ModItems.G17_PISTOL.get())) {
            shootTicks = 0;
        }
    }

    /**
     * The server sends ReloadFeedback ONLY after actually transferring ammo.
     * Restart the animation if reloading succeeds again later.
     */
    public static void reloadConfirmed() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getMainHandItem().is(ModItems.G17_PISTOL.get())) {
            reloadTicks = 0;
            shootTicks = -1;
        }
    }

    /**
     * Sample immutable interpolated animation pose. 'partialTick' is 0..1.
     * Every track is generated mathematically in this mod, rather than
     * imported from another mod's copyrighted animation assets.
     */
    public static Pose sample(float partialTick, float ads) {
        float partial = Mth.clamp(partialTick, 0.0F, 1.0F);
        float aim = Mth.clamp(ads, 0.0F, 1.0F);

        float shot = 0.0F;
        if (shootTicks >= 0) {
            float t = Mth.clamp((shootTicks + partial) / SHOOT_TICKS, 0.0F, 1.0F);
            // Sharp initial snap, then a longer damped recovery.
            shot = t < 0.21F
                    ? smooth(t / 0.21F)
                    : 1.0F - smooth((t - 0.21F) / 0.79F);
        }

        float reload = 0.0F;
        float reloadAngle = 0.0F;
        float supportReach = 0.0F;
        if (reloadTicks >= 0) {
            float t = Mth.clamp((reloadTicks + partial) / RELOAD_TICKS, 0.0F, 1.0F);
            // Original, independent movement curves:
            // lower gun -> tilt to examine magazine -> re-seat -> return.
            reload = track(t, RELOAD_LOWER_KEYS);
            reloadAngle = track(t, RELOAD_TILT_KEYS);
            supportReach = track(t, SUPPORT_REACH_KEYS);
        }

        float equip = 0.0F;
        if (equipTicks >= 0) {
            float t = Mth.clamp((equipTicks + partial) / EQUIP_TICKS, 0.0F, 1.0F);
            equip = 1.0F - smooth(t);
        }

        float moving = Mth.lerp(partial, previousWalkStrength, walkStrength);
        float phase = (ageTicks + partial) * 0.73F;
        float hipWeight = 1.0F - aim * 0.83F;
        float bobX = Mth.sin(phase) * moving * 0.020F * hipWeight;
        float bobY = Math.abs(Mth.cos(phase)) * moving * 0.017F * hipWeight;
        float breathing = Mth.sin((ageTicks + partial) * 0.14F)
                * (0.0020F + (1.0F - aim) * 0.0015F);

        return new Pose(shot, reload, reloadAngle, supportReach, equip,
                bobX, bobY, breathing);
    }

    /**
     * Animation clock for the locally licensed TaCZ G17 rig. This never
     * changes server rules: it merely exposes the active cosmetic track.
     * Action durations are normalized to the original keyframe clip lengths
     * by G17LicensedAnimation.
     */
    public static Timeline timeline(float partialTick) {
        float p = Mth.clamp(partialTick, 0.0F, 1.0F);
        if (reloadTicks >= 0) {
            return new Timeline("reload_tactical",
                    Mth.clamp((reloadTicks+p)/RELOAD_TICKS,0.0F,1.0F));
        }
        if (shootTicks >= 0) {
            return new Timeline("shoot",
                    Mth.clamp((shootTicks+p)/SHOOT_TICKS,0.0F,1.0F));
        }
        if (equipTicks >= 0) {
            return new Timeline("draw",
                    Mth.clamp((equipTicks+p)/EQUIP_TICKS,0.0F,1.0F));
        }
        return new Timeline("static_idle",0.0F);
    }

    public record Timeline(String name, float progress) {}

    /**
     * Lightweight item-model frame for separately moving slide and magazine.
     * This is evaluated only for the local first-person pistol. Every target
     * model is made from our own contributed Blockbench G17 geometry.
     *
     * 0 = normal, .2 = slide partly back, .4 = slide fully back,
     * .6 = magazine partially extracted, .8 = magazine extracted.
     */
    public static float modelFrame(float partialTick) {
        float p = Mth.clamp(partialTick, 0.0F, 1.0F);
        if (reloadTicks >= 0) {
            float t = Mth.clamp((reloadTicks + p) / RELOAD_TICKS, 0.0F, 1.0F);
            if (t >= 0.32F && t < 0.68F) {
                return 0.8F;
            }
            if (t >= 0.20F && t < 0.83F) {
                return 0.6F;
            }
            return 0.0F;
        }
        if (shootTicks >= 0) {
            float t = Mth.clamp((shootTicks + p) / SHOOT_TICKS, 0.0F, 1.0F);
            if (t < 0.24F) {
                return 0.4F;
            }
            if (t < 0.56F) {
                return 0.2F;
            }
        }
        return 0.0F;
    }

    /** Piecewise smooth keyframe curve: (time,value) pairs, no external data. */
    private static float track(float time, float[] points) {
        if (time <= points[0]) {
            return points[1];
        }
        for (int i = 2; i < points.length; i += 2) {
            float t1 = points[i];
            if (time <= t1) {
                float t0 = points[i - 2];
                float alpha = smooth((time - t0) / (t1 - t0));
                return Mth.lerp(alpha, points[i - 1], points[i + 1]);
            }
        }
        return points[points.length - 1];
    }

    private static float smooth(float t) {
        float x = Mth.clamp(t, 0.0F, 1.0F);
        return x * x * (3.0F - 2.0F * x);
    }

    public record Pose(float shot, float reload, float reloadAngle,
                       float supportReach, float equip, float bobX,
                       float bobY, float breathing) {}
}
