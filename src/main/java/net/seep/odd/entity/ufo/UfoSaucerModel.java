package net.seep.odd.entity.ufo;

import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public class UfoSaucerModel extends GeoModel<UfoSaucerEntity> {
    @Override
    public Identifier getModelResource(UfoSaucerEntity entity) {
        return new Identifier("odd", "geo/ufo_saucer.geo.json");
    }
    @Override
    public Identifier getTextureResource(UfoSaucerEntity entity) {
        return new Identifier("odd", "textures/entity/ufo_saucer.png");
    }
    @Override
    public Identifier getAnimationResource(UfoSaucerEntity entity) {
        return new Identifier("odd", "animations/ufo_saucer.animation.json");
    }

    // Optional: separate emissive (“glow”) texture path
}
