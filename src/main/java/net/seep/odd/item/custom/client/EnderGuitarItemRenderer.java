package net.seep.odd.item.custom.client;

import net.seep.odd.item.custom.EnderGuitarItem;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class EnderGuitarItemRenderer extends GeoItemRenderer<EnderGuitarItem> {
    public EnderGuitarItemRenderer() {
        super(new EnderGuitarItemModel());
    }
}
