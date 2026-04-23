package net.seep.odd.entity.star_ride;

import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public final class StarRideModel extends GeoModel<StarRideEntity> {
    @Override
    public Identifier getModelResource(StarRideEntity animatable) {
        return new Identifier("odd", "geo/star_ride.geo.json");
    }

    @Override
    public Identifier getTextureResource(StarRideEntity animatable) {
        return new Identifier("odd", "textures/entity/star_ride.png");
    }

    @Override
    public Identifier getAnimationResource(StarRideEntity animatable) {
        return new Identifier("odd", "animations/star_ride.animation.json");
    }
}
