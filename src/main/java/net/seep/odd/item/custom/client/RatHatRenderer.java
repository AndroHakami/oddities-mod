package net.seep.odd.item.custom.client;

import net.seep.odd.item.custom.RatHatItem;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

public class RatHatRenderer extends GeoArmorRenderer<RatHatItem> {
    public RatHatRenderer() {
        super(new RatHatModel());
    }
}