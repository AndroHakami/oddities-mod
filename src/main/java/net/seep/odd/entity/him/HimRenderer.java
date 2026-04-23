package net.seep.odd.entity.him;

import net.minecraft.client.render.entity.EntityRendererFactory;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public final class HimRenderer extends GeoEntityRenderer<HimEntity> {
    public HimRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new HimModel());
        this.addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0.42F;
    }
}
