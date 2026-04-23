package net.seep.odd.entity.granny;

import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public final class GrannyModel extends GeoModel<GrannyEntity> {
    @Override
    public Identifier getModelResource(GrannyEntity entity) {
        return new Identifier("odd", "geo/granny.geo.json");
    }

    @Override
    public Identifier getTextureResource(GrannyEntity entity) {
        return new Identifier("odd", "textures/entity/granny.png");
    }

    @Override
    public Identifier getAnimationResource(GrannyEntity entity) {
        return new Identifier("odd", "animations/granny.animation.json");
    }
}