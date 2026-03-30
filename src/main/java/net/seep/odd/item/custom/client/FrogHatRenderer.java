package net.seep.odd.item.custom.client;

import net.seep.odd.item.custom.FrogHatItem;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

public class FrogHatRenderer extends GeoArmorRenderer<FrogHatItem> {
    public FrogHatRenderer() {
        super(new FrogHatModel());
    }
}