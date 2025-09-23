package net.seep.odd.entity.outerman;

import net.minecraft.util.Identifier;

import software.bernie.geckolib.model.GeoModel;

public class OuterManModel extends GeoModel<OuterManEntity> {
    @Override
    public Identifier getModelResource(OuterManEntity entity) {
        return new Identifier("odd", "geo/outerman.geo.json");
    }
    @Override
    public Identifier getTextureResource(OuterManEntity entity) {
        return new Identifier("odd", "textures/entity/outerman.png");
    }
    @Override
    public Identifier getAnimationResource(OuterManEntity entity) {
        return new Identifier("odd", "animations/outerman.animation.json");
    }

    // Optional: separate emissive (“glow”) texture path
}
