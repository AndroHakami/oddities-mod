package net.seep.odd.entity.ufo;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.seep.odd.entity.ufo.client.UfoClientFx;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public final class OuterMechRenderer extends GeoEntityRenderer<OuterMechEntity> {
    public OuterMechRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new OuterMechModel());
        UfoClientFx.init();
        this.addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 4.0f;
    }

    @Override
    public RenderLayer getRenderType(OuterMechEntity animatable, Identifier texture, VertexConsumerProvider buffers, float partialTick) {
        return RenderLayer.getEntityTranslucent(texture);
    }

    @Override
    public void render(OuterMechEntity entity, float entityYaw, float partialTicks, MatrixStack matrices, VertexConsumerProvider buffers, int light) {
        super.render(entity, entityYaw, partialTicks, matrices, buffers, light);
    }
}