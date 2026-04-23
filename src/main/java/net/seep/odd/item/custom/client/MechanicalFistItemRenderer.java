package net.seep.odd.item.custom.client;

import net.seep.odd.item.custom.MechanicalFistItem;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class MechanicalFistItemRenderer extends GeoItemRenderer<MechanicalFistItem> {
    public MechanicalFistItemRenderer() {
        super(new MechanicalFistItemModel());
    }
}
