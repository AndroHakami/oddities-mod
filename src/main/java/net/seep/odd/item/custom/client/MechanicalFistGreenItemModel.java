package net.seep.odd.item.custom.client;

import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.item.custom.MechanicalFistGreenItem;
import software.bernie.geckolib.model.GeoModel;

public class MechanicalFistGreenItemModel extends GeoModel<MechanicalFistGreenItem> {
    @Override
    public Identifier getModelResource(MechanicalFistGreenItem item) {
        return new Identifier(Oddities.MOD_ID, "geo/mechanical_fist.geo.json");
    }

    @Override
    public Identifier getTextureResource(MechanicalFistGreenItem item) {
        return new Identifier(Oddities.MOD_ID, "textures/item/mechanical_fist_green.png");
    }

    @Override
    public Identifier getAnimationResource(MechanicalFistGreenItem item) {
        return new Identifier(Oddities.MOD_ID, "animations/mechanical_fist.animation.json");
    }
}
