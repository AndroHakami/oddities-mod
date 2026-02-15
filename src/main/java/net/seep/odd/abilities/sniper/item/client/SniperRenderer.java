package net.seep.odd.abilities.sniper.item.client;

import software.bernie.geckolib.renderer.GeoItemRenderer;
import net.seep.odd.abilities.sniper.item.SniperItem;

public class SniperRenderer extends GeoItemRenderer<SniperItem> {
    public SniperRenderer() {
        super(new SniperModel());
    }
}
