package net.seep.odd.entity.dragoness;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class UfoProtectorRenderer extends GeoEntityRenderer<UfoProtectorEntity> {
    public UfoProtectorRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new UfoProtectorModel());
        this.shadowRadius = 0.0f;
    }

    @Override
    public void render(UfoProtectorEntity entity, float entityYaw, float partialTicks, MatrixStack matrices, VertexConsumerProvider buffers, int packedLight) {
        matrices.push();
        float scale = entity.getSpawnScale(partialTicks);
        matrices.scale(scale, scale, scale);
        super.render(entity, entityYaw, partialTicks, matrices, buffers, packedLight);
        matrices.pop();

        UfoProtectorBeamRenderer.render(entity, partialTicks, matrices, buffers);
    }
}
