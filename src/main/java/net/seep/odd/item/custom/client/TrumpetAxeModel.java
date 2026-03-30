// FILE: src/main/java/net/seep/odd/item/client/TrumpetAxeModel.java
package net.seep.odd.item.custom.client;

import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.item.custom.TrumpetAxeItem;
import software.bernie.geckolib.model.GeoModel;

public class TrumpetAxeModel extends GeoModel<TrumpetAxeItem> {
    @Override
    public Identifier getModelResource(TrumpetAxeItem animatable) {
        return new Identifier(Oddities.MOD_ID, "geo/trumpet_axe.geo.json");
    }

    @Override
    public Identifier getTextureResource(TrumpetAxeItem animatable) {
        return new Identifier(Oddities.MOD_ID, "textures/item/trumpet_axe.png");
    }

    @Override
    public Identifier getAnimationResource(TrumpetAxeItem animatable) {
        return new Identifier(Oddities.MOD_ID, "animations/trumpet_axe.animation.json");
    }
}