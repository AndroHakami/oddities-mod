package net.seep.odd.entity.car;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;


import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public final class RiderCarRenderer extends GeoEntityRenderer<RiderCarEntity> {
    public RiderCarRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new RiderCarModel());
        // Auto-detects "<base>_glow.png"

        this.shadowRadius = 0.6f;
    }

    @Override
    public RenderLayer getRenderType(RiderCarEntity animatable, Identifier texture, VertexConsumerProvider buffers, float partialTick) {
        return RenderLayer.getEntityTranslucent(texture);
    }

    @Override
    public void render(RiderCarEntity entity, float entityYaw, float partialTicks, MatrixStack matrices, VertexConsumerProvider buffers, int light) {
        if (entity.getVelocity().lengthSquared() > 0.01) {
            matrices.translate(0, Math.sin((entity.age + partialTicks) * 0.08) * 0.02, 0);
        }
        super.render(entity, entityYaw, partialTicks, matrices, buffers, light);
    }
}
