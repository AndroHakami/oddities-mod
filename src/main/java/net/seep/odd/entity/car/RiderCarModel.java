package net.seep.odd.entity.car;

import net.minecraft.util.Identifier;

import software.bernie.geckolib.model.GeoModel;

public class RiderCarModel extends GeoModel<RiderCarEntity> {
    @Override
    public Identifier getModelResource(RiderCarEntity entity) {
        return new Identifier("odd", "geo/car.geo.json");
    }
    @Override
    public Identifier getTextureResource(RiderCarEntity entity) {
        return new Identifier("odd", "textures/entity/car.png");
    }
    @Override
    public Identifier getAnimationResource(RiderCarEntity entity) {
        return new Identifier("odd", "animations/car.animation.json");
    }

    // Optional: separate emissive (“glow”) texture path
}
