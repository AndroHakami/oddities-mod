// net/seep/odd/abilities/tamer/client/TameBallModel.java
package net.seep.odd.abilities.tamer.client;

import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.abilities.tamer.projectile.TameBallEntity;
import software.bernie.geckolib.model.GeoModel;

public class TameBallModel extends GeoModel<TameBallEntity> {
    @Override public Identifier getModelResource(TameBallEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "geo/tame_ball.geo.json");
    }
    @Override public Identifier getTextureResource(TameBallEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "textures/item/tame_ball.png");
    }
    @Override public Identifier getAnimationResource(TameBallEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "animations/tame_ball.animation.json");
    }
}
