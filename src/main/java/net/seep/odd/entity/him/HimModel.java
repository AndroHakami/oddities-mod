package net.seep.odd.entity.him;

import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public final class HimModel extends GeoModel<HimEntity> {
    @Override
    public Identifier getModelResource(HimEntity entity) {
        return new Identifier("odd", "geo/him.geo.json");
    }

    @Override
    public Identifier getTextureResource(HimEntity entity) {
        return new Identifier("odd", "textures/entity/him.png");
    }

    @Override
    public Identifier getAnimationResource(HimEntity entity) {
        return new Identifier("odd", "animations/him.animation.json");
    }
}
