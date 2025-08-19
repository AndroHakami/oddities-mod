package net.seep.odd.item.ghost.client;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.util.Identifier;
import net.seep.odd.item.ghost.GhostHandItem;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public final class GhostHandRenderer extends GeoItemRenderer<GhostHandItem> {

    public GhostHandRenderer() {
        super(new GhostHandModel());

        // Emissive pass: looks for a texture named like "<base>_glow.png"
        // right next to your normal texture. No recursion, no custom reRender calls.
        this.addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    // Make the base texture honor alpha (PNG transparency)
    @Override
    public RenderLayer getRenderType(GhostHandItem animatable, Identifier texture,
                                     VertexConsumerProvider buffers, float partialTick) {
        return RenderLayer.getEntityTranslucent(texture);
    }
}
