package net.seep.odd.item.custom.client;

import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.item.custom.MechanicalFistBlueItem;
import software.bernie.geckolib.model.GeoModel;

public class MechanicalFistBlueItemModel extends GeoModel<MechanicalFistBlueItem> {
    @Override
    public Identifier getModelResource(MechanicalFistBlueItem item) {
        return new Identifier(Oddities.MOD_ID, "geo/mechanical_fist.geo.json");
    }

    @Override
    public Identifier getTextureResource(MechanicalFistBlueItem item) {
        return new Identifier(Oddities.MOD_ID, "textures/item/mechanical_fist_blue.png");
    }

    @Override
    public Identifier getAnimationResource(MechanicalFistBlueItem item) {
        return new Identifier(Oddities.MOD_ID, "animations/mechanical_fist.animation.json");
    }
}
