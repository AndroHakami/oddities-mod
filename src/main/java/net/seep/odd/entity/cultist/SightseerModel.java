package net.seep.odd.entity.cultist;

import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public final class SightseerModel extends GeoModel<SightseerEntity> {
    @Override
    public Identifier getModelResource(SightseerEntity entity) {
        return new Identifier("odd", "geo/sightseer.geo.json");
    }

    @Override
    public Identifier getTextureResource(SightseerEntity entity) {
        return new Identifier("odd", "textures/entity/cultist/sightseer.png");
    }

    @Override
    public Identifier getAnimationResource(SightseerEntity entity) {
        return new Identifier("odd", "animations/sightseer.animation.json");
    }
}
