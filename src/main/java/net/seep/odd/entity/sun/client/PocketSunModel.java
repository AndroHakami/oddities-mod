package net.seep.odd.entity.sun.client;

import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.entity.sun.PocketSunEntity;
import software.bernie.geckolib.model.GeoModel;

public final class PocketSunModel extends GeoModel<PocketSunEntity> {
    @Override
    public Identifier getModelResource(PocketSunEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "geo/pocket_sun.geo.json");
    }

    @Override
    public Identifier getTextureResource(PocketSunEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "textures/entity/pocket_sun.png");
    }

    @Override
    public Identifier getAnimationResource(PocketSunEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "animations/pocket_sun.animation.json");
    }
}
