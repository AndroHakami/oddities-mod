
package net.seep.odd.item.outerblaster.client;

import net.seep.odd.item.outerblaster.OuterBlasterItem;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public final class OuterBlasterRenderer extends GeoItemRenderer<OuterBlasterItem> {
    public OuterBlasterRenderer() {
        super(new OuterBlasterModel());
        this.addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }
}
