package net.seep.odd.entity.rake;

import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public final class RakeModel extends GeoModel<RakeEntity> {
    @Override
    public Identifier getModelResource(RakeEntity entity) {
        return new Identifier("odd", "geo/rake.geo.json");
    }

    @Override
    public Identifier getTextureResource(RakeEntity entity) {
        return new Identifier("odd", "textures/entity/rake.png");
    }

    @Override
    public Identifier getAnimationResource(RakeEntity entity) {
        return new Identifier("odd", "animations/rake.animation.json");
    }
}
