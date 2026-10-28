package com.zombieapocalypse.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.zombieapocalypse.ZombieApocalypseMod;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

/**
 * Small NeoForge 1.21.1 adapter for the user's locally licensed original
 * TaCZ Glock 17 animation JSON. Only the animation resource and bone curves
 * are read: none of TaCZ's old Forge 1.20.1 runtime is loaded or copied.
 *
 * The gun and each player-skinned hand follow the same source clip clock.
 * Other mod gameplay and animation fallback remain intact when the user has
 * not installed licensed assets locally.
 */
public final class G17LicensedAnimation {
    private static final ResourceLocation ANIMATION =
            ResourceLocation.fromNamespaceAndPath(
                    ZombieApocalypseMod.MOD_ID,
                    "licensed_tacz/g17/glock_17.animation.json");

    private static JsonObject clips;
    private static boolean attempted;

    private G17LicensedAnimation() {}

    public static boolean installed() {
        loadOnce();
        return clips != null;
    }

    private static void loadOnce() {
        if (attempted) {
            return;
        }
        attempted = true;
        Minecraft mc = Minecraft.getInstance();
        try {
            var found = mc.getResourceManager().getResource(ANIMATION);
            if (found.isEmpty()) {
                return;
            }
            try (var reader = new InputStreamReader(
                    found.get().open(), StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                clips = root.getAsJsonObject("animations");
                ZombieApocalypseMod.LOGGER.info(
                        "Loaded licensed TaCZ G17 animation tracks (resource-only bridge)");
            }
        } catch (Exception error) {
            ZombieApocalypseMod.LOGGER.warn(
                    "Failed to load optional G17 animation resource; using existing animation",error);
            clips = null;
        }
    }

    public record Vector(float x, float y, float z) {
        public Vector minus(Vector other) {
            return new Vector(x-other.x, y-other.y, z-other.z);
        }

        public Vector plus(Vector other) {
            return new Vector(x+other.x, y+other.y, z+other.z);
        }
    }

    public record Bone(Vector position, Vector rotation) {}

    private static JsonObject object(JsonObject parent, String name) {
        if (parent == null || !parent.has(name)
                || !parent.get(name).isJsonObject()) {
            return null;
        }
        return parent.getAsJsonObject(name);
    }

    private static Vector fromArray(JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return new Vector(0,0,0);
        }
        if (value.isJsonObject()) {
            JsonObject obj = value.getAsJsonObject();
            return fromArray(obj.get("post"));
        }
        if (!value.isJsonArray()) {
            return new Vector(0,0,0);
        }
        JsonArray a = value.getAsJsonArray();
        if (a.size() < 3) {
            return new Vector(0,0,0);
        }
        try {
            return new Vector(a.get(0).getAsFloat(),
                    a.get(1).getAsFloat(),a.get(2).getAsFloat());
        } catch (NumberFormatException | UnsupportedOperationException error) {
            // The original JSON can contain MoLang expressions. Those are
            // intentionally not evaluated in this lightweight Java adapter.
            return new Vector(0,0,0);
        }
    }

    private static Vector interpolate(Vector a, Vector b, float portion) {
        return new Vector(a.x()+(b.x()-a.x())*portion,
                a.y()+(b.y()-a.y())*portion,
                a.z()+(b.z()-a.z())*portion);
    }

    private static Vector sample(JsonElement track, float time) {
        if (track == null || track.isJsonNull()) {
            return new Vector(0,0,0);
        }
        if (track.isJsonArray()) {
            return fromArray(track);
        }
        if (!track.isJsonObject()) {
            return new Vector(0,0,0);
        }
        TreeMap<Float, Vector> frames = new TreeMap<>();
        for (Map.Entry<String, JsonElement> e :
                track.getAsJsonObject().entrySet()) {
            try {
                frames.put(Float.parseFloat(e.getKey()),fromArray(e.getValue()));
            } catch (NumberFormatException ignored) {
                // Unrecognized keyframe expression (not a timestamp).
            }
        }
        if (frames.isEmpty()) {
            return new Vector(0,0,0);
        }
        Map.Entry<Float,Vector> next = frames.ceilingEntry(time);
        Map.Entry<Float,Vector> prior = frames.floorEntry(time);
        if (prior == null) return frames.firstEntry().getValue();
        if (next == null) return frames.lastEntry().getValue();
        if (prior.getKey().equals(next.getKey())) return prior.getValue();
        float p=(time-prior.getKey())/(next.getKey()-prior.getKey());
        return interpolate(prior.getValue(),next.getValue(),p);
    }

    private static Bone sampleClip(String clipName, String boneName,
                                   float progress) {
        JsonObject clip=object(clips,clipName);
        JsonObject bones=object(clip,"bones");
        JsonObject bone=object(bones,boneName);
        if (bone == null) {
            return new Bone(new Vector(0,0,0),new Vector(0,0,0));
        }
        float duration = clip.has("animation_length")
                ? clip.get("animation_length").getAsFloat() : 0.0F;
        float time=Math.max(0,Math.min(1,progress))*duration;
        return new Bone(sample(bone.get("position"),time),
                sample(bone.get("rotation"),time));
    }

    /** Bone offset relative to the normal static_idle grip or slide. */
    public static Bone delta(String boneName,float partialTick) {
        loadOnce();
        if (clips == null) {
            return new Bone(new Vector(0,0,0),new Vector(0,0,0));
        }
        G17AnimationState.Timeline timeline =
                G17AnimationState.timeline(partialTick);
        Bone current=sampleClip(timeline.name(),boneName,timeline.progress());
        Bone idle=sampleClip("static_idle",boneName,0);
        return new Bone(current.position().minus(idle.position()),
                current.rotation().minus(idle.rotation()));
    }

    /**
     * Apply actual TaCZ root + gun motion to the item *and* the hand parent.
     * Keep the original G17 camera/iron sight calibration as the base pose.
     * Rotations are intentionally damped because we do not copy TaCZ's camera
     * animation, and it would otherwise apply recoil twice.
     */
    public static void applyGunMotion(PoseStack pose,float partialTick) {
        if (!installed()) return;
        Bone root=delta("root",partialTick);
        Bone gun=delta("g17",partialTick);
        Vector p=root.position().plus(gun.position());
        Vector r=root.rotation().plus(gun.rotation());
        pose.translate(p.x()/64.0F,p.y()/64.0F,p.z()/64.0F);
        pose.mulPose(Axis.XP.rotationDegrees(r.x()*0.35F));
        pose.mulPose(Axis.YP.rotationDegrees(r.y()*0.35F));
        pose.mulPose(Axis.ZP.rotationDegrees(r.z()*0.35F));
    }

    /** Apply actual right/left hand keyframe deltas to a skinned arm. */
    public static void applyHandMotion(PoseStack pose, boolean right,
                                       float partialTick) {
        if (!installed()) return;
        Bone hand=delta(right ? "righthand" : "lefthand",partialTick);
        Vector p=hand.position();
        Vector r=hand.rotation();
        pose.translate(p.x()/64.0F,p.y()/64.0F,p.z()/64.0F);
        pose.mulPose(Axis.XP.rotationDegrees(r.x()*0.38F));
        pose.mulPose(Axis.YP.rotationDegrees(r.y()*0.38F));
        pose.mulPose(Axis.ZP.rotationDegrees(r.z()*0.38F));
    }
}
