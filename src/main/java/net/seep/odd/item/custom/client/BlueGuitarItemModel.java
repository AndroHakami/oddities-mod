package net.seep.odd.item.custom.client;

import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.item.custom.BlueGuitarItem;
import software.bernie.geckolib.model.GeoModel;

public class BlueGuitarItemModel extends GeoModel<BlueGuitarItem> {
    @Override
    public Identifier getModelResource(BlueGuitarItem item) {
        return new Identifier(Oddities.MOD_ID, "geo/guitar.geo.json");
    }

    @Override
    public Identifier getTextureResource(BlueGuitarItem item) {
        return new Identifier(Oddities.MOD_ID, "textures/item/blue_guitar.png");
    }

    @Override
    public Identifier getAnimationResource(BlueGuitarItem item) {
        return new Identifier(Oddities.MOD_ID, "animations/guitar.animation.json");
    }
}
