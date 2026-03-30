package net.seep.odd.item.custom.client;

import net.seep.odd.item.custom.CrownItem;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

public class CrownRenderer extends GeoArmorRenderer<CrownItem> {
    public CrownRenderer() {
        super(new CrownModel());
    }
}