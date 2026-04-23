package net.seep.odd.abilities.shift.entity;

import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public final class DecoyModel extends GeoModel<DecoyEntity> {
    @Override
    public Identifier getModelResource(DecoyEntity entity) {
        return new Identifier("odd", "geo/decoy.geo.json");
    }

    @Override
    public Identifier getTextureResource(DecoyEntity entity) {
        return new Identifier("odd", "textures/entity/decoy.png");
    }

    @Override
    public Identifier getAnimationResource(DecoyEntity entity) {
        return new Identifier("odd", "animations/decoy.animation.json");
    }
}
