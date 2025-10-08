package net.seep.odd.abilities.icewitch.client;

import net.minecraft.util.Identifier;
import net.seep.odd.abilities.icewitch.IceSpellAreaEntity;
import software.bernie.geckolib.model.GeoModel;

public class IceSpellAreaModel extends GeoModel<IceSpellAreaEntity> {
    @Override
    public Identifier getModelResource(IceSpellAreaEntity entity) {
        // A flat plane (sigil) model — make a simple .geo.json (single quad) facing UP
        return new Identifier("odd", "geo/ice_sigil.geo.json");
    }

    @Override
    public Identifier getTextureResource(IceSpellAreaEntity entity) {
        // reuse your existing sigil texture
        return new Identifier("odd", "textures/effects/ice_sigil.png");
    }

    @Override
    public Identifier getAnimationResource(IceSpellAreaEntity entity) {
        // animations: "appear" (scale 0->1), "idle" (slow spin), "vanish" (scale/alpha out)
        return new Identifier("odd", "animations/ice_sigil.animation.json");
    }
}
