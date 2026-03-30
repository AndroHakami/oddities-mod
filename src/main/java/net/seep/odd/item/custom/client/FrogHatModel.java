package net.seep.odd.item.custom.client;

import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.item.custom.FrogHatItem;
import software.bernie.geckolib.model.GeoModel;

public class FrogHatModel extends GeoModel<FrogHatItem> {
    @Override
    public Identifier getModelResource(FrogHatItem animatable) {
        return new Identifier(Oddities.MOD_ID, "geo/frog_hat.geo.json");
    }

    @Override
    public Identifier getTextureResource(FrogHatItem animatable) {
        return new Identifier(Oddities.MOD_ID, "textures/armor/frog_hat.png");
    }

    @Override
    public Identifier getAnimationResource(FrogHatItem animatable) {
        return new Identifier(Oddities.MOD_ID, "animations/frog_hat.animation.json");
    }
}