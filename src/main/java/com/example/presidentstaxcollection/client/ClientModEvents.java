package com.example.presidentstaxcollection.client;

import com.example.presidentstaxcollection.PresidentsTaxCollection;
import com.example.presidentstaxcollection.registry.ModEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
        modid = PresidentsTaxCollection.MOD_ID, // usa la constante nueva
        bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT
)
public class ClientModEvents {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        System.out.println("REGISTRANDO RENDERER TAX_COLLECTOR");

        event.registerEntityRenderer(
                ModEntities.TAX_COLLECTOR.get(),
                TaxCollectorRenderer::new
        );
    }
}