// FILE: src/main/java/net/seep/odd/entity/rotten_roots/ShroomModel.java
package net.seep.odd.entity.rotten_roots;

import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public final class ShroomModel extends GeoModel<ShroomEntity> {
    @Override
    public Identifier getModelResource(ShroomEntity entity) {
        return new Identifier("odd", "geo/shroom.geo.json");
    }

    @Override
    public Identifier getTextureResource(ShroomEntity entity) {
        return new Identifier("odd", "textures/entity/rotten_roots/shroom.png");
    }

    @Override
    public Identifier getAnimationResource(ShroomEntity entity) {
        return new Identifier("odd", "animations/shroom.animation.json");
    }
}