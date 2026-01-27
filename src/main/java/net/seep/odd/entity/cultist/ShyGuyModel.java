package net.seep.odd.entity.cultist;

import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public final class ShyGuyModel extends GeoModel<ShyGuyEntity> {
    @Override
    public Identifier getModelResource(ShyGuyEntity entity) {
        return new Identifier("odd", "geo/shy_guy.geo.json");
    }

    @Override
    public Identifier getTextureResource(ShyGuyEntity entity) {
        return new Identifier("odd", "textures/entity/cultist/shy_guy.png");
    }

    @Override
    public Identifier getAnimationResource(ShyGuyEntity entity) {
        return new Identifier("odd", "animations/shy_guy.animation.json");
    }
}
