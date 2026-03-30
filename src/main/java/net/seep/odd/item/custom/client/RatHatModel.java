package net.seep.odd.item.custom.client;

import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.item.custom.RatHatItem;
import software.bernie.geckolib.model.GeoModel;

public class RatHatModel extends GeoModel<RatHatItem> {
    @Override
    public Identifier getModelResource(RatHatItem animatable) {
        return new Identifier(Oddities.MOD_ID, "geo/rat_hat.geo.json");
    }

    @Override
    public Identifier getTextureResource(RatHatItem animatable) {
        return new Identifier(Oddities.MOD_ID, "textures/armor/rat_hat.png");
    }

    @Override
    public Identifier getAnimationResource(RatHatItem animatable) {
        return new Identifier(Oddities.MOD_ID, "animations/rat_hat.animation.json");
    }
}