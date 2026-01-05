package net.seep.odd.abilities.firesword.client;

import net.seep.odd.abilities.firesword.item.FireSwordItem;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public class FireSwordItemRenderer extends GeoItemRenderer<FireSwordItem> {
    public FireSwordItemRenderer() {

        super(new FireSwordItemModel());
        this.addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }
}
