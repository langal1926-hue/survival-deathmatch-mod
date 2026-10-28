package com.zombieapocalypse;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Entry point for the Zombie Apocalypse SMP mod.
 * Gameplay features will be registered in later development milestones.
 */
@Mod(ZombieApocalypseMod.MOD_ID)
public class ZombieApocalypseMod {
    public static final String MOD_ID = "zombieapocalypse";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ZombieApocalypseMod() {
        LOGGER.info("Zombie Apocalypse SMP mod initialized.");
    }
}
