package net.seep.odd.item.custom.client;

import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.item.custom.EnderGuitarItem;
import software.bernie.geckolib.model.GeoModel;

public class EnderGuitarItemModel extends GeoModel<EnderGuitarItem> {
    @Override
    public Identifier getModelResource(EnderGuitarItem item) {
        return new Identifier(Oddities.MOD_ID, "geo/guitar.geo.json");
    }

    @Override
    public Identifier getTextureResource(EnderGuitarItem item) {
        return new Identifier(Oddities.MOD_ID, "textures/item/ender_guitar.png");
    }

    @Override
    public Identifier getAnimationResource(EnderGuitarItem item) {
        return new Identifier(Oddities.MOD_ID, "animations/guitar.animation.json");
    }
}
