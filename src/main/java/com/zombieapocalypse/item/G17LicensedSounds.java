package com.zombieapocalypse.item;

import com.zombieapocalypse.ZombieApocalypseMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The audio clips themselves are NEVER stored in the public source tree.
 * When the user's locally licensed TaCZ G17 resources were imported into the
 * mod JAR, these sound events refer to those clips. Otherwise the existing
 * vanilla sound effects remain active to preserve the current experience.
 */
public final class G17LicensedSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, ZombieApocalypseMod.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> SHOOT =
            register("g17_tacz_shoot");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHOOT_THIRD_PERSON =
            register("g17_tacz_shoot_3p");
    public static final DeferredHolder<SoundEvent, SoundEvent> RELOAD =
            register("g17_tacz_reload");
    public static final DeferredHolder<SoundEvent, SoundEvent> MAG_OUT =
            register("g17_tacz_magout");
    public static final DeferredHolder<SoundEvent, SoundEvent> MAG_IN =
            register("g17_tacz_magin");
    public static final DeferredHolder<SoundEvent, SoundEvent> DRAW =
            register("g17_tacz_draw");

    private static final boolean INSTALLED =
            G17LicensedSounds.class.getResource(
                    "/assets/zombieapocalypse/sounds/g17_tacz/glock17_shoot.ogg") != null;

    private static DeferredHolder<SoundEvent, SoundEvent> register(String path) {
        ResourceLocation name = ResourceLocation.fromNamespaceAndPath(
                ZombieApocalypseMod.MOD_ID, path);
        return SOUNDS.register(path,
                () -> SoundEvent.createVariableRangeEvent(name));
    }

    public static boolean installed() {
        return INSTALLED;
    }

    private G17LicensedSounds() {}
}
