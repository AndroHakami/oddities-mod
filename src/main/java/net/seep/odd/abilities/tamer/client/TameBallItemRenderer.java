package net.seep.odd.abilities.tamer.client;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.util.Identifier;
import net.seep.odd.abilities.tamer.item.TameBallItem;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public final class TameBallItemRenderer extends GeoItemRenderer<TameBallItem> {
    public TameBallItemRenderer() {
        super(new TameBallItemModel());

    }

}
