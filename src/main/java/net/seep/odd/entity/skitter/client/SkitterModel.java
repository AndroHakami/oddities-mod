package net.seep.odd.entity.skitter.client;

import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.entity.skitter.SkitterEntity;
import software.bernie.geckolib.model.GeoModel;

public final class SkitterModel extends GeoModel<SkitterEntity> {
    @Override
    public Identifier getModelResource(SkitterEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "geo/skitter.geo.json");
    }

    @Override
    public Identifier getTextureResource(SkitterEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "textures/entity/skitter.png");
    }

    @Override
    public Identifier getAnimationResource(SkitterEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "animations/skitter.animation.json");
    }
}