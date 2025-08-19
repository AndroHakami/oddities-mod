// net/seep/odd/entity/creepy/client/CreepyModel.java
package net.seep.odd.entity.creepy.client;

import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;
import net.seep.odd.entity.creepy.CreepyEntity;

public final class CreepyModel extends GeoModel<CreepyEntity> {
    private static final Identifier MODEL   = new Identifier("odd", "geo/creepy.geo.json");
    private static final Identifier TEXTURE = new Identifier("odd", "textures/entity/creepy.png");
    private static final Identifier ANIMS   = new Identifier("odd", "animations/creepy.animation.json");

    @Override public Identifier getModelResource(CreepyEntity animatable)    { return MODEL; }
    @Override public Identifier getTextureResource(CreepyEntity animatable)  { return TEXTURE; }
    @Override public Identifier getAnimationResource(CreepyEntity animatable){ return ANIMS; }
}
