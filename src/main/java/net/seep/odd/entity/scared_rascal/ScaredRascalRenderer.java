package net.seep.odd.entity.scared_rascal;

import net.minecraft.client.render.entity.EntityRendererFactory;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public final class ScaredRascalRenderer extends GeoEntityRenderer<ScaredRascalEntity> {
    public ScaredRascalRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new ScaredRascalModel());
        this.addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0.35F;
    }
}
