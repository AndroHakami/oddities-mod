// net/seep/odd/item/custom/client/CosmicKatanaItemRenderer.java
package net.seep.odd.item.custom.client;

import net.seep.odd.item.custom.CosmicKatanaItem;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public class CosmicKatanaItemRenderer extends GeoItemRenderer<CosmicKatanaItem> {
    public CosmicKatanaItemRenderer() {
        super(new CosmicKatanaItemModel());

        // optional glow layer (remove if you don't want emissive)
        this.addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }
}