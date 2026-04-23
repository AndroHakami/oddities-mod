package net.seep.odd.entity.rake;

import net.minecraft.client.render.entity.EntityRendererFactory;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public final class RakeRenderer extends GeoEntityRenderer<RakeEntity> {
    public RakeRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new RakeModel());
        this.addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0.5F;
    }
}
