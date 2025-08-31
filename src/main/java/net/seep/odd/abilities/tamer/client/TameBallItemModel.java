// net/seep/odd/abilities/tamer/client/TameBallItemModel.java
package net.seep.odd.abilities.tamer.client;

import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.abilities.tamer.item.TameBallItem;
import software.bernie.geckolib.model.GeoModel;

public class TameBallItemModel extends GeoModel<TameBallItem> {
    @Override public Identifier getModelResource(TameBallItem anim) {
        return new Identifier(Oddities.MOD_ID, "geo/tame_ball.geo.json");
    }
    @Override public Identifier getTextureResource(TameBallItem anim) {
        return new Identifier(Oddities.MOD_ID, "textures/entity/tame_ball.png");
    }
    @Override public Identifier getAnimationResource(TameBallItem anim) {
        return new Identifier(Oddities.MOD_ID, "animations/tame_ball.animation.json");
    }
}
