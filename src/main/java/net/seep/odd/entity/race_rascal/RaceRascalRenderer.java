package net.seep.odd.entity.race_rascal;

import net.minecraft.client.render.entity.EntityRendererFactory;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public final class RaceRascalRenderer extends GeoEntityRenderer<RaceRascalEntity> {
    public RaceRascalRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new RaceRascalModel());
        this.addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0.35F;
    }
}
