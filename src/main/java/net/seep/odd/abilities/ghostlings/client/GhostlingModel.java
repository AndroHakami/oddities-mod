package net.seep.odd.abilities.ghostlings.client;

import net.minecraft.util.Identifier;
import net.seep.odd.abilities.ghostlings.entity.GhostlingEntity;
import software.bernie.geckolib.model.GeoModel;

public class GhostlingModel extends GeoModel<GhostlingEntity> {
    @Override public Identifier getModelResource(GhostlingEntity a) { return new Identifier("odd", "geo/ghostling.geo.json"); }
    @Override public Identifier getTextureResource(GhostlingEntity a) { return new Identifier("odd", "textures/entity/ghostling.png"); }
    @Override public Identifier getAnimationResource(GhostlingEntity a) { return new Identifier("odd", "animations/ghostling.animation.json"); }
}
