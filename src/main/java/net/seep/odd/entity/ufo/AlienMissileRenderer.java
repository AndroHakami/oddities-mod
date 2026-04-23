package net.seep.odd.entity.ufo;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.seep.odd.entity.ufo.client.UfoClientFx;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public final class AlienMissileRenderer extends GeoEntityRenderer<AlienMissileEntity> {
    public AlienMissileRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new AlienMissileModel());
        UfoClientFx.init();
        this.addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0.45f;
    }

    @Override
    public RenderLayer getRenderType(AlienMissileEntity animatable, Identifier texture, VertexConsumerProvider buffers, float partialTick) {
        return RenderLayer.getEntityTranslucent(texture);
    }

    @Override
    public void render(AlienMissileEntity entity, float entityYaw, float partialTicks, MatrixStack matrices, VertexConsumerProvider buffers, int light) {
        // Missile is a bit large, so slightly tone the render scale down.
        float scale = 0.82f * Math.max(1.0f, entity.getRenderScale());

        // Fixed axis correction for the model.
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f));

        // Use actual entity pitch from the current travel vector.
        float pitch = MathHelper.lerp(partialTicks, entity.prevPitch, entity.getPitch());
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));

        matrices.scale(scale, scale, scale);

        super.render(entity, entityYaw, partialTicks, matrices, buffers, light);
    }
}