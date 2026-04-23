package net.seep.odd.item.custom.client;

import net.seep.odd.item.custom.GuitarItem;
import software.bernie.geckolib.renderer.GeoItemRenderer;
// import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public class GuitarItemRenderer extends GeoItemRenderer<GuitarItem> {
    public GuitarItemRenderer() {
        super(new GuitarItemModel());

        // Only add this if you're actually making a glowmask texture
        // this.addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }
}