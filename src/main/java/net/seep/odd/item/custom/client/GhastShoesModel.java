package net.seep.odd.item.custom.client;

import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.item.custom.GhastShoesItem;
import software.bernie.geckolib.model.GeoModel;

public class GhastShoesModel extends GeoModel<GhastShoesItem> {
    @Override
    public Identifier getModelResource(GhastShoesItem animatable) {
        return new Identifier(Oddities.MOD_ID, "geo/ghast_shoes.geo.json");
    }

    @Override
    public Identifier getTextureResource(GhastShoesItem animatable) {
        return new Identifier(Oddities.MOD_ID, "textures/armor/ghast_shoes.png");
    }

    @Override
    public Identifier getAnimationResource(GhastShoesItem animatable) {
        return new Identifier(Oddities.MOD_ID, "animations/ghast_shoes.animation.json");
    }
}