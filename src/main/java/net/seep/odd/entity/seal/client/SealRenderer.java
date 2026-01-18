package net.seep.odd.entity.seal.client;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.seep.odd.entity.seal.SealEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class SealRenderer extends GeoEntityRenderer<SealEntity> {

    public SealRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new SealModel());
        this.shadowRadius = 0.35f;
    }
}
