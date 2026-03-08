// FILE: src/main/java/net/seep/odd/entity/rotten_roots/ShroomRenderer.java
package net.seep.odd.entity.rotten_roots;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class ShroomRenderer extends GeoEntityRenderer<ShroomEntity> {
    public ShroomRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new ShroomModel());
        this.shadowRadius = 0.45f;
    }

    @Override
    public RenderLayer getRenderType(ShroomEntity animatable, Identifier texture, VertexConsumerProvider buffers, float partialTick) {
        return RenderLayer.getEntityTranslucent(texture);
    }
}