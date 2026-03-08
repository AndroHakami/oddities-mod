package net.seep.odd.entity.skitter.client;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.seep.odd.entity.skitter.SkitterEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public final class SkitterRenderer extends GeoEntityRenderer<SkitterEntity> {
    public SkitterRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new SkitterModel());
        this.shadowRadius = 0.55f;
    }
}