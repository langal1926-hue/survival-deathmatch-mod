package com.zombieapocalypse;

import com.mojang.logging.LogUtils;
import com.zombieapocalypse.entity.InfectedZombie;
import com.zombieapocalypse.entity.ModEntities;
import com.zombieapocalypse.item.ModItems;
import com.zombieapocalypse.loot.ModLootModifiers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import org.slf4j.Logger;

/** Entry point for the Zombie Apocalypse SMP mod. */
@Mod(ZombieApocalypseMod.MOD_ID)
public class ZombieApocalypseMod {
    public static final String MOD_ID = "zombieapocalypse";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ZombieApocalypseMod(IEventBus modEventBus) {
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModLootModifiers.SERIALIZERS.register(modEventBus);
        modEventBus.addListener(this::registerAttributes);
        // TaCZ is a required, separate NeoForge mod that registers its own
        // weapon input, packets, animation, HUD and sound pipeline.
        // Do NOT register the legacy custom G17 networking/sound handlers.
        LOGGER.info("Zombie Apocalypse SMP initialized with native TaCZ weapon integration.");
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.INFECTED.get(), InfectedZombie.createInfectedAttributes().build());
    }
}
