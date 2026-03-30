package net.seep.odd.item.custom.client;

import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.item.custom.GuitarItem;
import software.bernie.geckolib.model.GeoModel;

public class GuitarItemModel extends GeoModel<GuitarItem> {
    @Override
    public Identifier getModelResource(GuitarItem item) {
        return new Identifier(Oddities.MOD_ID, "geo/guitar.geo.json");
    }

    @Override
    public Identifier getTextureResource(GuitarItem item) {
        return new Identifier(Oddities.MOD_ID, "textures/item/guitar.png");
    }

    @Override
    public Identifier getAnimationResource(GuitarItem item) {
        return new Identifier(Oddities.MOD_ID, "animations/guitar.animation.json");
    }
}