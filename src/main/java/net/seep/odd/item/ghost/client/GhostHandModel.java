package net.seep.odd.item.ghost.client;

import net.minecraft.util.Identifier;
import net.seep.odd.item.ghost.GhostHandItem;
import software.bernie.geckolib.model.GeoModel;

public final class GhostHandModel extends GeoModel<GhostHandItem> {
    @Override public Identifier getModelResource(GhostHandItem item) {
        return new Identifier("odd", "geo/ghost_hand.geo.json");
    }
    @Override public Identifier getTextureResource(GhostHandItem item) {
        return new Identifier("odd", "textures/entity/ghost_hand/ghost_hand.png");
    }
    @Override public Identifier getAnimationResource(GhostHandItem item) {
        return new Identifier("odd", "animations/ghost_hand.animation.json");

    }


}
