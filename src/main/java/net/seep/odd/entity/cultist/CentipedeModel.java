package net.seep.odd.entity.cultist;

import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public final class CentipedeModel extends GeoModel<CentipedeEntity> {
    @Override
    public Identifier getModelResource(CentipedeEntity entity) {
        return new Identifier("odd", "geo/centipede.geo.json");
    }

    @Override
    public Identifier getTextureResource(CentipedeEntity entity) {
        return new Identifier("odd", "textures/entity/cultist/centipede.png");
    }

    @Override
    public Identifier getAnimationResource(CentipedeEntity entity) {
        return new Identifier("odd", "animations/centipede.animation.json");
    }
}
