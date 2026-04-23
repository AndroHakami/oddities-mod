package net.seep.odd.item.custom.client;

import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.item.custom.BlueTrumpetAxeItem;
import software.bernie.geckolib.model.GeoModel;

public class BlueTrumpetAxeModel extends GeoModel<BlueTrumpetAxeItem> {
    @Override
    public Identifier getModelResource(BlueTrumpetAxeItem animatable) {
        return new Identifier(Oddities.MOD_ID, "geo/trumpet_axe.geo.json");
    }

    @Override
    public Identifier getTextureResource(BlueTrumpetAxeItem animatable) {
        return new Identifier(Oddities.MOD_ID, "textures/item/trumpet_axe_blue.png");
    }

    @Override
    public Identifier getAnimationResource(BlueTrumpetAxeItem animatable) {
        return new Identifier(Oddities.MOD_ID, "animations/trumpet_axe.animation.json");
    }
}
