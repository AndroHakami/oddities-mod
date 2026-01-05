package net.seep.odd.entity.firefly.client;

import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.entity.firefly.FireflyEntity;
import software.bernie.geckolib.model.GeoModel;

public class FireflyModel extends GeoModel<FireflyEntity> {
    @Override
    public Identifier getModelResource(FireflyEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "geo/firefly.geo.json");
    }

    @Override
    public Identifier getTextureResource(FireflyEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "textures/entity/firefly.png");
    }

    @Override
    public Identifier getAnimationResource(FireflyEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "animations/firefly.animation.json");
    }
}
