package net.seep.odd.entity.scared_rascal;

import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public final class ScaredRascalModel extends GeoModel<ScaredRascalEntity> {
    @Override
    public Identifier getModelResource(ScaredRascalEntity entity) {
        return new Identifier("odd", "geo/rascal.geo.json");
    }

    @Override
    public Identifier getTextureResource(ScaredRascalEntity entity) {
        return new Identifier("odd", "textures/entity/scared_rascal.png");
    }

    @Override
    public Identifier getAnimationResource(ScaredRascalEntity entity) {
        return new Identifier("odd", "animations/rascal.animation.json");
    }
}
