package net.seep.odd.entity.outerman;

import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public final class OuterManGunnerModel extends GeoModel<OuterManGunnerEntity> {
    @Override
    public Identifier getModelResource(OuterManGunnerEntity entity) {
        return new Identifier("odd", "geo/outerman_gunner.geo.json");
    }

    @Override
    public Identifier getTextureResource(OuterManGunnerEntity entity) {
        return new Identifier("odd", "textures/entity/outerman_gunner.png");
    }

    @Override
    public Identifier getAnimationResource(OuterManGunnerEntity entity) {
        return new Identifier("odd", "animations/outerman_gunner.animation.json");
    }
}