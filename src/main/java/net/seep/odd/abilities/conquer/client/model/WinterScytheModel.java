package net.seep.odd.abilities.conquer.client.model;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.minecraft.util.Identifier;

import net.seep.odd.abilities.conquer.item.WinterScytheItem;

import software.bernie.geckolib.model.GeoModel;

@Environment(EnvType.CLIENT)
public final class WinterScytheModel extends GeoModel<WinterScytheItem> {

    @Override
    public Identifier getModelResource(WinterScytheItem animatable) {
        return new Identifier("odd", "geo/winter_scythe.geo.json");
    }

    @Override
    public Identifier getTextureResource(WinterScytheItem animatable) {
        return new Identifier("odd", "textures/item/winter_scythe.png");
    }

    @Override
    public Identifier getAnimationResource(WinterScytheItem animatable) {
        return new Identifier("odd", "animations/winter_scythe.animation.json");
    }
}
