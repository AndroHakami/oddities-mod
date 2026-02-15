package net.seep.odd.abilities.sniper.item.client;

import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.abilities.sniper.item.SniperItem;
import software.bernie.geckolib.model.GeoModel;

public final class SniperModel extends GeoModel<SniperItem> {
    @Override
    public Identifier getModelResource(SniperItem animatable) {
        return new Identifier(Oddities.MOD_ID, "geo/sniper.geo.json");
    }

    @Override
    public Identifier getTextureResource(SniperItem animatable) {
        return new Identifier(Oddities.MOD_ID, "textures/item/sniper.png");
    }

    @Override
    public Identifier getAnimationResource(SniperItem animatable) {
        return new Identifier(Oddities.MOD_ID, "animations/sniper.animation.json");
    }
}
