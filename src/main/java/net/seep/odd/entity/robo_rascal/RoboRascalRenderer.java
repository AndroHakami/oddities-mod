package net.seep.odd.entity.robo_rascal;

import net.minecraft.client.render.entity.EntityRendererFactory;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public final class RoboRascalRenderer extends GeoEntityRenderer<RoboRascalEntity> {
    public RoboRascalRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new RoboRascalModel());
        this.addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0.8F;
    }
}
