package net.seep.odd.entity.dragoness;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public final class DragonessRenderer extends GeoEntityRenderer<DragonessEntity> {
    public DragonessRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new DragonessModel());
        this.addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 1.45f;
    }

    @Override
    public RenderLayer getRenderType(DragonessEntity animatable, Identifier texture, VertexConsumerProvider buffers, float partialTick) {
        return RenderLayer.getEntityTranslucent(texture);
    }

    @Override
    public void render(DragonessEntity entity, float entityYaw, float partialTicks, MatrixStack matrices,
                       VertexConsumerProvider buffers, int packedLight) {
        if (entity.isAirbornePose()) {
            matrices.translate(0.0D, Math.sin((entity.age + partialTicks) * 0.12D) * 0.06D, 0.0D);
        }
        super.render(entity, entityYaw, partialTicks, matrices, buffers, packedLight);
    }
}
