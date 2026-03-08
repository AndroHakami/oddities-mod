// FILE: src/main/java/net/seep/odd/entity/rotten_roots/ElderShroomModel.java
package net.seep.odd.entity.rotten_roots;

import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public final class ElderShroomModel extends GeoModel<ElderShroomEntity> {
    @Override
    public Identifier getModelResource(ElderShroomEntity entity) {
        return new Identifier("odd", "geo/elder_shroom.geo.json");
    }

    @Override
    public Identifier getTextureResource(ElderShroomEntity entity) {
        return new Identifier("odd", "textures/entity/rotten_roots/elder_shroom.png");
    }

    @Override
    public Identifier getAnimationResource(ElderShroomEntity entity) {
        return new Identifier("odd", "animations/elder_shroom.animation.json");
    }
}