package net.seep.odd.item.custom.client;

import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.item.custom.MechanicalFistItem;
import software.bernie.geckolib.model.GeoModel;

public class MechanicalFistItemModel extends GeoModel<MechanicalFistItem> {
    @Override
    public Identifier getModelResource(MechanicalFistItem item) {
        return new Identifier(Oddities.MOD_ID, "geo/mechanical_fist.geo.json");
    }

    @Override
    public Identifier getTextureResource(MechanicalFistItem item) {
        return new Identifier(Oddities.MOD_ID, "textures/item/mechanical_fist.png");
    }

    @Override
    public Identifier getAnimationResource(MechanicalFistItem item) {
        return new Identifier(Oddities.MOD_ID, "animations/mechanical_fist.animation.json");
    }
}
