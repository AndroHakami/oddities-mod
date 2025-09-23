package net.seep.odd.entity.outerman;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

import net.seep.odd.entity.ufo.UfoSaucerModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public final class OuterManRenderer extends GeoEntityRenderer<OuterManEntity> {
    public OuterManRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new OuterManModel());
        // Auto-detects "<base>_glow.png"
        this.addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0.6f;
    }

    @Override
    public RenderLayer getRenderType(OuterManEntity animatable, Identifier texture, VertexConsumerProvider buffers, float partialTick) {
        return RenderLayer.getEntityTranslucent(texture);
    }

    @Override
    public void render(OuterManEntity entity, float entityYaw, float partialTicks, MatrixStack matrices, VertexConsumerProvider buffers, int light) {
        if (entity.getVelocity().lengthSquared() > 0.01) {
            matrices.translate(0, Math.sin((entity.age + partialTicks) * 0.08) * 0.02, 0);
        }
        super.render(entity, entityYaw, partialTicks, matrices, buffers, light);
    }
}
