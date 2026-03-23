package com.mpisoni.presidentstaxcollection.registry;

import com.mpisoni.presidentstaxcollection.PresidentsTaxCollection;
import com.mpisoni.presidentstaxcollection.entity.TaxCollectorEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Centraliza el registro de todas las entidades del mod.
 * Se integra con ExampleMod.java vía bus.register() y bus.addListener().
 */
@Mod.EventBusSubscriber(
        modid = PresidentsTaxCollection.MOD_ID, // constante nueva
        bus = Mod.EventBusSubscriber.Bus.MOD
)public class ModEntities {

    // DeferredRegister para EntityType — patrón estándar de Forge 1.20.1
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, PresidentsTaxCollection.MOD_ID);

    /**
     * Registro del Tax Collector.
     *
     * Tamaño del hitbox: ancho 0.6, alto 1.95 (similar a un villager).
     * MobCategory.MISC: no spawna naturalmente, se invoca por scheduler o /summon.
     *
     * Para hacer un spawn natural en biomas específicos, cambiar a MONSTER
     * y configurar SpawnPlacements en un BiomeLoadingEvent.
     */
    public static final RegistryObject<EntityType<TaxCollectorEntity>> TAX_COLLECTOR =
            ENTITY_TYPES.register("tax_collector",
                    () -> EntityType.Builder.<TaxCollectorEntity>of(
                                    TaxCollectorEntity::new,
                                    MobCategory.MISC
                            )
                            .sized(0.6f, 1.95f)       // hitbox width x height
                            .clientTrackingRange(10)   // bloques de tracking en cliente
                            .updateInterval(3)         // ticks entre sync servidor→cliente
                            .build("tax_collector")    // debe coincidir con el nombre del registro
            );

    /**
     * Vincula los atributos del mob al EntityType.
     * Se dispara en el bus MOD durante la fase de inicialización.
     *
     * Si olvidás este evento, el juego crashea con:
     * "No attribute supplier found for entity type"
     */
    @SubscribeEvent
    public static void onAttributeCreate(EntityAttributeCreationEvent event) {
        event.put(TAX_COLLECTOR.get(), TaxCollectorEntity.createAttributes().build());
    }


}