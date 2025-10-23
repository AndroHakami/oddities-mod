package net.seep.odd.abilities.gamble.item.client;

import software.bernie.geckolib.renderer.GeoItemRenderer;
import net.seep.odd.abilities.gamble.item.GambleRevolverItem;

public class GambleRevolverRenderer extends GeoItemRenderer<GambleRevolverItem> {
    public GambleRevolverRenderer() {
        super(new GambleRevolverModel());  // use your explicit model (paths below)
    }
}
