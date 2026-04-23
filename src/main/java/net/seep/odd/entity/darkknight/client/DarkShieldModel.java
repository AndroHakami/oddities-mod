package net.seep.odd.entity.darkknight.client;

import net.minecraft.util.Identifier;
import net.seep.odd.entity.darkknight.DarkShieldEntity;
import software.bernie.geckolib.model.GeoModel;

public class DarkShieldModel extends GeoModel<DarkShieldEntity> {
    @Override
    public Identifier getModelResource(DarkShieldEntity animatable) {
        return new Identifier("odd", "geo/dark_shield.geo.json");
    }

    @Override
    public Identifier getTextureResource(DarkShieldEntity animatable) {
        return new Identifier("odd", "textures/entity/dark_shield.png");
    }

    @Override
    public Identifier getAnimationResource(DarkShieldEntity animatable) {
        return new Identifier("odd", "animations/dark_shield.animation.json");
    }
}
