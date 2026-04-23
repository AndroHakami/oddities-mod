package net.seep.odd.item.custom.client;

import net.seep.odd.item.custom.MechanicalFistBlueItem;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class MechanicalFistBlueItemRenderer extends GeoItemRenderer<MechanicalFistBlueItem> {
    public MechanicalFistBlueItemRenderer() {
        super(new MechanicalFistBlueItemModel());
    }
}
