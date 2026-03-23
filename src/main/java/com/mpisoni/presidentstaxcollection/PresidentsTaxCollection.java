package com.mpisoni.presidentstaxcollection;

import com.mpisoni.presidentstaxcollection.registry.ModEntities;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Clase principal del mod.
 *
 * Cambios respecto al template original:
 * 1. Registra ModEntities.ENTITY_TYPES en el bus MOD
 * 2. Registra la Config de tipo SERVER
 */
@Mod(PresidentsTaxCollection.MOD_ID)
public class PresidentsTaxCollection {   // <-- CAMBIO AQUÍ
    public static final String MOD_ID = "presidentstaxcollection";

    public PresidentsTaxCollection() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        // ── Registros ────────────────────────────────────────────────────────
        ModEntities.ENTITY_TYPES.register(modBus);

        // ── Config ───────────────────────────────────────────────────────────
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.SERVER, Config.SPEC);

        // ── Setup ────────────────────────────────────────────────────────────
        modBus.addListener(this::commonSetup);

        // Registra esta clase en el bus FORGE para eventos de runtime
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // Espacio para setup adicional en el futuro
    }
}