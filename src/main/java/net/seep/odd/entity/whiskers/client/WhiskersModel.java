// FILE: src/main/java/net/seep/odd/entity/whiskers/client/WhiskersModel.java
package net.seep.odd.entity.whiskers.client;

import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.entity.whiskers.WhiskersEntity;
import software.bernie.geckolib.model.GeoModel;

public final class WhiskersModel extends GeoModel<WhiskersEntity> {

    @Override
    public Identifier getModelResource(WhiskersEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "geo/whiskers.geo.json");
    }

    @Override
    public Identifier getTextureResource(WhiskersEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "textures/entity/whiskers.png");
    }

    @Override
    public Identifier getAnimationResource(WhiskersEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "animations/whiskers.animation.json");
    }
}