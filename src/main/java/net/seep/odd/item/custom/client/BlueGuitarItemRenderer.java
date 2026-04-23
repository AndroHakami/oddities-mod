package net.seep.odd.item.custom.client;

import net.seep.odd.item.custom.BlueGuitarItem;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class BlueGuitarItemRenderer extends GeoItemRenderer<BlueGuitarItem> {
    public BlueGuitarItemRenderer() {
        super(new BlueGuitarItemModel());
    }
}
