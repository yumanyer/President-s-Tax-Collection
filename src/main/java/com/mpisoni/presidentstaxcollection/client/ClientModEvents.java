package com.mpisoni.presidentstaxcollection.client;

import com.mpisoni.presidentstaxcollection.PresidentsTaxCollection;
import com.mpisoni.presidentstaxcollection.registry.ModEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod.EventBusSubscriber(
        modid = PresidentsTaxCollection.MOD_ID, // usa la constante nueva
        bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT
)
public class ClientModEvents {
    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        LOGGER.info("Registrando renderer para Tax Collector");

        event.registerEntityRenderer(
                ModEntities.TAX_COLLECTOR.get(),
                TaxCollectorRenderer::new
        );
    }
}