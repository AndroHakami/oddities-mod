// FILE: src/main/java/net/seep/odd/entity/skull_bird/client/SkullBirdModel.java
package net.seep.odd.entity.skull_bird.client;

import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.entity.skull_bird.SkullBirdEntity;
import software.bernie.geckolib.model.GeoModel;

public final class SkullBirdModel extends GeoModel<SkullBirdEntity> {

    @Override
    public Identifier getModelResource(SkullBirdEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "geo/skull_bird.geo.json");
    }

    @Override
    public Identifier getTextureResource(SkullBirdEntity animatable) {
        int v = animatable.getVariantId();
        return new Identifier(Oddities.MOD_ID, "textures/entity/skull_bird/skull_bird_" + v + ".png");
    }

    @Override
    public Identifier getAnimationResource(SkullBirdEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "animations/skull_bird.animation.json");
    }
}