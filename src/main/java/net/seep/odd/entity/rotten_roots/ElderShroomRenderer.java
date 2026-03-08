// FILE: src/main/java/net/seep/odd/entity/rotten_roots/ElderShroomRenderer.java
package net.seep.odd.entity.rotten_roots;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class ElderShroomRenderer extends GeoEntityRenderer<ElderShroomEntity> {
    public ElderShroomRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new ElderShroomModel());
        this.shadowRadius = 0.55f;
    }

    @Override
    public RenderLayer getRenderType(ElderShroomEntity animatable, Identifier texture, VertexConsumerProvider buffers, float partialTick) {
        return RenderLayer.getEntityTranslucent(texture);
    }
}