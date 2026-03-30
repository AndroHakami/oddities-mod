package net.seep.odd.item.custom.client;

import net.seep.odd.item.custom.GhastShoesItem;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

public class GhastShoesRenderer extends GeoArmorRenderer<GhastShoesItem> {
    public GhastShoesRenderer() {
        super(new GhastShoesModel());
    }
}