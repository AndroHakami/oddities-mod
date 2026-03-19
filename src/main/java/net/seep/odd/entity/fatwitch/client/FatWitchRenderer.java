package net.seep.odd.entity.fatwitch.client;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.seep.odd.entity.fatwitch.FatWitchEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class FatWitchRenderer extends GeoEntityRenderer<FatWitchEntity> {
    public FatWitchRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new FatWitchModel());
        this.shadowRadius = 0.8f;
    }

    @Override
    public void render(FatWitchEntity entity, float entityYaw, float partialTicks, MatrixStack matrices, VertexConsumerProvider buffers, int light) {
        float bob = 0.14f + (float) Math.sin((entity.age + partialTicks) * 0.12f) * 0.08f;
        matrices.translate(0.0D, bob, 0.0D);
        super.render(entity, entityYaw, partialTicks, matrices, buffers, light);
    }
}