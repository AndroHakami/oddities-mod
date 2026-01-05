package net.seep.odd.abilities.conquer.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.seep.odd.abilities.conquer.client.model.WinterScytheModel;
import net.seep.odd.abilities.conquer.item.WinterScytheItem;

import software.bernie.geckolib.renderer.GeoItemRenderer;

@Environment(EnvType.CLIENT)
public final class WinterScytheRenderer extends GeoItemRenderer<WinterScytheItem> {
    public WinterScytheRenderer() {
        super(new WinterScytheModel());
    }
}
