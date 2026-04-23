package net.seep.odd.entity.robo_rascal;

import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public final class RoboRascalModel extends GeoModel<RoboRascalEntity> {
    @Override
    public Identifier getModelResource(RoboRascalEntity entity) {
        return new Identifier("odd", "geo/robo_rascal.geo.json");
    }

    @Override
    public Identifier getTextureResource(RoboRascalEntity entity) {
        return new Identifier("odd", "textures/entity/robo_rascal.png");
    }

    @Override
    public Identifier getAnimationResource(RoboRascalEntity entity) {
        return new Identifier("odd", "animations/robo_rascal.animation.json");
    }
}
