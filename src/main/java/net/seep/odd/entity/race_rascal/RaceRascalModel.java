package net.seep.odd.entity.race_rascal;

import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public final class RaceRascalModel extends GeoModel<RaceRascalEntity> {
    @Override
    public Identifier getModelResource(RaceRascalEntity entity) {
        return new Identifier("odd", "geo/rascal.geo.json");
    }

    @Override
    public Identifier getTextureResource(RaceRascalEntity entity) {
        return new Identifier("odd", "textures/entity/racing_rascal.png");
    }

    @Override
    public Identifier getAnimationResource(RaceRascalEntity entity) {
        return new Identifier("odd", "animations/rascal.animation.json");
    }
}
