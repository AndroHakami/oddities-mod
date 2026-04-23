package net.seep.odd.entity.granny;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class GrannyRenderer extends GeoEntityRenderer<GrannyEntity> {
    public GrannyRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new GrannyModel());
        this.shadowRadius = 0.75f;
    }

    @Override
    public RenderLayer getRenderType(GrannyEntity animatable, Identifier texture, VertexConsumerProvider buffers, float partialTick) {
        return RenderLayer.getEntityTranslucent(texture);
    }

    @Override
    public void render(GrannyEntity entity, float entityYaw, float partialTicks, MatrixStack matrices, VertexConsumerProvider buffers, int light) {
        if (entity.getVelocity().lengthSquared() > 0.001D) {
            matrices.translate(0.0D, Math.sin((entity.age + partialTicks) * 0.16D) * 0.015D, 0.0D);
        }
        super.render(entity, entityYaw, partialTicks, matrices, buffers, light);
    }
}