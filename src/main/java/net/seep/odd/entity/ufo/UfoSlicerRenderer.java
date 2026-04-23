package net.seep.odd.entity.ufo;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.util.Identifier;
import net.seep.odd.entity.ufo.client.UfoClientFx;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public final class UfoSlicerRenderer extends GeoEntityRenderer<UfoSlicerEntity> {
    public UfoSlicerRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new UfoSlicerModel());
        UfoClientFx.init();
        this.addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0.45f;
    }

    @Override
    public RenderLayer getRenderType(UfoSlicerEntity animatable, Identifier texture, VertexConsumerProvider buffers, float partialTick) {
        return RenderLayer.getEntityTranslucent(texture);
    }

    @Override
    public void render(UfoSlicerEntity entity, float entityYaw, float partialTicks, MatrixStack matrices, VertexConsumerProvider buffers, int light) {
        if (entity.getVelocity().lengthSquared() > 0.002) {
            matrices.translate(0.0, Math.sin((entity.age + partialTicks) * 0.22) * 0.018, 0.0);
        }
        super.render(entity, entityYaw, partialTicks, matrices, buffers, light);
    }
}