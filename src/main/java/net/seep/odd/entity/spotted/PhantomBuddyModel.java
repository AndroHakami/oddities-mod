package net.seep.odd.entity.spotted;

import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public class PhantomBuddyModel extends GeoModel<PhantomBuddyEntity> {
    @Override
    public Identifier getModelResource(PhantomBuddyEntity entity) {
        return new Identifier("odd", "geo/phantom_buddy.geo.json");
    }
    @Override
    public Identifier getTextureResource(PhantomBuddyEntity entity) {
        return new Identifier("odd", "textures/entity/phantom_buddy.png");
    }
    @Override
    public Identifier getAnimationResource(PhantomBuddyEntity entity) {
        return new Identifier("odd", "animations/phantom_buddy.animation.json");
    }
    // If you add a glow map, AutoGlowingGeoLayer will look for "phantom_buddy_glow.png"
}
