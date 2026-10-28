package com.zombieapocalypse.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.util.AllowAttachmentTagMatcher;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Default-pack, 14-gun allowlist for the Zombie Apocalypse SMP server.
 *
 * TaCZ gun/attachment items are generic registry types whose REAL identities
 * are stored in custom item data, not in BuiltInRegistries.ITEM. Always query
 * TaCZ IGun / IAttachment rather than looking only at the item registry ID.
 *
 * Edit the single JSON source in data/zombieapocalypse/tacz_weapon_allowlist.json.
 * An omitted (null) scope means iron sights or an original TaCZ built-in
 * scope (AUG). Other attachment types (grips, mags, lasers, etc.) are banned.
 */
public final class TaCZWeaponAllowlist {
    public record Loadout(ResourceLocation scope, ResourceLocation muzzle) {}

    private static final String MANIFEST =
            "/data/zombieapocalypse/tacz_weapon_allowlist.json";

    private static final Map<ResourceLocation, Loadout> GUNS = loadManifest();

    private static final Set<ResourceLocation> LOOSE_ATTACHMENTS =
            collectPermittedLooseAttachments(GUNS);

    private TaCZWeaponAllowlist() {}

    private static Map<ResourceLocation, Loadout> loadManifest() {
        try (InputStream stream = TaCZWeaponAllowlist.class.getResourceAsStream(MANIFEST)) {
            if (stream == null) {
                throw new IllegalStateException("Required TaCZ weapon allowlist not found: " + MANIFEST);
            }

            JsonObject root = JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.get("format_version").getAsInt() != 1) {
                throw new IllegalStateException("Unsupported TaCZ weapon allowlist format");
            }

            Map<ResourceLocation, Loadout> guns = new HashMap<>();
            JsonArray entries = root.getAsJsonArray("guns");
            for (JsonElement entry : entries) {
                JsonObject row = entry.getAsJsonObject();
                ResourceLocation id = ResourceLocation.parse(row.get("id").getAsString());
                ResourceLocation scope = nullableId(row.get("scope"));
                ResourceLocation muzzle = nullableId(row.get("muzzle"));
                if (!id.getNamespace().equals("tacz")
                        || (scope != null && !scope.getNamespace().equals("tacz"))
                        || (muzzle != null && !muzzle.getNamespace().equals("tacz"))
                        || guns.putIfAbsent(id, new Loadout(scope, muzzle)) != null) {
                    throw new IllegalStateException("Invalid or duplicated TaCZ gun ID: " + id);
                }
            }
            if (guns.size() != 14) {
                throw new IllegalStateException("Expected exactly 14 allowed TaCZ guns, got " + guns.size());
            }
            return Collections.unmodifiableMap(guns);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load TaCZ server weapon policy", e);
        }
    }

    private static ResourceLocation nullableId(JsonElement element) {
        return element == null || element.isJsonNull()
                ? null : ResourceLocation.parse(element.getAsString());
    }

    private static Set<ResourceLocation> collectPermittedLooseAttachments(
            Map<ResourceLocation, Loadout> loadouts) {
        Set<ResourceLocation> out = new HashSet<>();
        loadouts.values().forEach(loadout -> {
            if (loadout.scope() != null) out.add(loadout.scope());
            if (loadout.muzzle() != null) out.add(loadout.muzzle());
        });
        return Set.copyOf(out);
    }

    /** True only for the fourteen approved gun data IDs (not item registry IDs). */
    public static boolean isAllowedGunId(ResourceLocation gunId) {
        return GUNS.containsKey(gunId);
    }

    /**
     * Item pickups / server inventory scans reject ALL other TaCZ guns and
     * all stand-alone attachments except the selected sights and suppressors.
     * Non-TaCZ items (including ammo) are not part of this first phase.
     */
    public static boolean isAllowedInventoryStack(ItemStack stack) {
        if (stack.isEmpty()) return true;
        if (stack.getItem() instanceof IGun gun) {
            return isAllowedGunId(gun.getGunId(stack));
        }
        if (stack.getItem() instanceof IAttachment attachment) {
            return LOOSE_ATTACHMENTS.contains(attachment.getAttachmentId(stack));
        }
        return true;
    }

    /**
     * A selected TaCZ gun may carry ONLY its OWN selected scope and muzzle.
     * This also covers unauthorized attachments added using commands, a
     * creative inventory, another add-on, or imported saved data.
     *
     * TaCZ built-in sights, such as the AUG's scope_aug_default, are not
     * separately installed attachments and are left untouched.
     */
    public static boolean isApprovedGunWithAttachments(ItemStack stack) {
        if (!(stack.getItem() instanceof IGun gun)) return false;
        ResourceLocation gunId = gun.getGunId(stack);
        Loadout loadout = GUNS.get(gunId);
        if (loadout == null) return false;

        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) continue;
            ResourceLocation installed = gun.getAttachmentId(stack, type);
            if (DefaultAssets.isEmptyAttachmentId(installed)) continue;
            if (!isApprovedAttachmentForGun(gunId, type, installed)
                    || !gun.allowAttachmentType(stack, type)
                    || !AllowAttachmentTagMatcher.match(gunId, installed)) {
                return false;
            }
        }
        return true;
    }

    public static boolean isApprovedAttachmentForGun(
            ResourceLocation gunId, AttachmentType type, ResourceLocation attachmentId) {
        Loadout loadout = GUNS.get(gunId);
        if (loadout == null) return false;
        return switch (type) {
            case SCOPE -> attachmentId.equals(loadout.scope());
            case MUZZLE -> attachmentId.equals(loadout.muzzle());
            default -> false;
        };
    }

    /**
     * Remove invalid INSTALLED accessories while retaining a legitimate gun.
     * TaCZ unloadAttachment mutates the actual gun stack and does not spawn
     * the rejected accessory, preventing an inventory dupe exploit.
     */
    public static int stripIllegalAttachments(ItemStack stack, HolderLookup.Provider registries) {
        if (!(stack.getItem() instanceof IGun gun)) return 0;
        ResourceLocation id = gun.getGunId(stack);
        if (!isAllowedGunId(id)) return 0;

        int removed = 0;
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) continue;
            ResourceLocation installed = gun.getAttachmentId(stack, type);
            if (DefaultAssets.isEmptyAttachmentId(installed)) continue;
            if (!isApprovedAttachmentForGun(id, type, installed)
                    || !gun.allowAttachmentType(stack, type)
                    || !AllowAttachmentTagMatcher.match(id, installed)) {
                gun.unloadAttachment(registries, stack, type);
                removed++;
            }
        }
        return removed;
    }

    public static boolean hasApprovedSuppressor(ItemStack stack) {
        if (!(stack.getItem() instanceof IGun gun)) return false;
        ResourceLocation muzzle = gun.getAttachmentId(stack, AttachmentType.MUZZLE);
        return isApprovedAttachmentForGun(gun.getGunId(stack), AttachmentType.MUZZLE, muzzle)
                && muzzle.getPath().startsWith("muzzle_silencer_");
    }

    public static int approvedGunCount() {
        return GUNS.size();
    }

    public static Set<ResourceLocation> approvedGunIds() {
        return GUNS.keySet();
    }

    public static Set<ResourceLocation> approvedLooseAttachmentIds() {
        return LOOSE_ATTACHMENTS;
    }
}
