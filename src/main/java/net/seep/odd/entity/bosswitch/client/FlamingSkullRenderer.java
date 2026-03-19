package net.seep.odd.entity.bosswitch.client;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.seep.odd.entity.bosswitch.FlamingSkullEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public final class FlamingSkullRenderer extends GeoEntityRenderer<FlamingSkullEntity> {
    public FlamingSkullRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new FlamingSkullModel());
        this.addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0.2f;
    }
}