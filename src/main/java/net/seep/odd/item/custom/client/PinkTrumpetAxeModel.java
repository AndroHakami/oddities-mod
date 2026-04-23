package net.seep.odd.item.custom.client;

import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.item.custom.PinkTrumpetAxeItem;
import software.bernie.geckolib.model.GeoModel;

public class PinkTrumpetAxeModel extends GeoModel<PinkTrumpetAxeItem> {
    @Override
    public Identifier getModelResource(PinkTrumpetAxeItem animatable) {
        return new Identifier(Oddities.MOD_ID, "geo/trumpet_axe.geo.json");
    }

    @Override
    public Identifier getTextureResource(PinkTrumpetAxeItem animatable) {
        return new Identifier(Oddities.MOD_ID, "textures/item/trumpet_axe_pink.png");
    }

    @Override
    public Identifier getAnimationResource(PinkTrumpetAxeItem animatable) {
        return new Identifier(Oddities.MOD_ID, "animations/trumpet_axe.animation.json");
    }
}
