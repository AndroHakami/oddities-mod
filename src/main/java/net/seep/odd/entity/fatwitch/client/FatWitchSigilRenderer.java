package net.seep.odd.entity.fatwitch.client;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.seep.odd.entity.fatwitch.FatWitchSigilEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class FatWitchSigilRenderer extends GeoEntityRenderer<FatWitchSigilEntity> {
    public FatWitchSigilRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new FatWitchSigilModel());
        this.shadowRadius = 0.0f;
    }

    @Override
    public RenderLayer getRenderType(FatWitchSigilEntity animatable, Identifier texture, VertexConsumerProvider buffers, float partialTick) {
        return RenderLayer.getEntityTranslucent(texture);
    }

    @Override
    public void render(FatWitchSigilEntity entity, float entityYaw, float partialTicks, MatrixStack matrices, VertexConsumerProvider buffers, int light) {
        super.render(entity, entityYaw, partialTicks, matrices, buffers, light);
    }
}