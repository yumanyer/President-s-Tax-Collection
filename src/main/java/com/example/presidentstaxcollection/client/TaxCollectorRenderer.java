package com.example.presidentstaxcollection.client;

import com.example.presidentstaxcollection.entity.TaxCollectorEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

public class TaxCollectorRenderer extends HumanoidMobRenderer<TaxCollectorEntity, PlayerModel<TaxCollectorEntity>> {

    public TaxCollectorRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(TaxCollectorEntity entity) {
        return ResourceLocation.fromNamespaceAndPath(
                "minecraft",
                "textures/entity/villager/villager.png"
        );
    }
}