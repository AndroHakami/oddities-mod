package net.seep.odd.entity.rascal;

import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public final class RascalModel extends GeoModel<RascalEntity> {
    @Override
    public Identifier getModelResource(RascalEntity animatable) {
        return new Identifier("odd", "geo/rascal.geo.json");
    }

    @Override
    public Identifier getTextureResource(RascalEntity animatable) {
        return new Identifier("odd", "textures/entity/rascal.png");
    }

    @Override
    public Identifier getAnimationResource(RascalEntity animatable) {
        return new Identifier("odd", "animations/rascal.animation.json");
    }
}