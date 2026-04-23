package net.seep.odd.entity.scared_rascal_fight;

import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public final class ScaredRascalFightModel extends GeoModel<ScaredRascalFightEntity> {
    @Override
    public Identifier getModelResource(ScaredRascalFightEntity entity) {
        return new Identifier("odd", "geo/rascal.geo.json");
    }

    @Override
    public Identifier getTextureResource(ScaredRascalFightEntity entity) {
        return new Identifier("odd", "textures/entity/scared_rascal.png");
    }

    @Override
    public Identifier getAnimationResource(ScaredRascalFightEntity entity) {
        return new Identifier("odd", "animations/rascal.animation.json");
    }
}
