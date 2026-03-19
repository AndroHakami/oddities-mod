package net.seep.odd.entity.bosswitch.client;

import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.entity.bosswitch.FlamingSkullEntity;
import software.bernie.geckolib.model.GeoModel;

public final class FlamingSkullModel extends GeoModel<FlamingSkullEntity> {
    @Override
    public Identifier getModelResource(FlamingSkullEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "geo/flaming_skull.geo.json");
    }

    @Override
    public Identifier getTextureResource(FlamingSkullEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "textures/entity/flaming_skull.png");
    }

    @Override
    public Identifier getAnimationResource(FlamingSkullEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "animations/flaming_skull.animation.json");
    }
}