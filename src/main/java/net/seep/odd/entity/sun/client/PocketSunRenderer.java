package net.seep.odd.entity.sun.client;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.seep.odd.entity.sun.PocketSunEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public final class PocketSunRenderer extends GeoEntityRenderer<PocketSunEntity> {

    // visual-only multiplier so the geo model matches the real hitbox better
    private static final float VISUAL_SIZE_MULTIPLIER = 1.45f;

    public PocketSunRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new PocketSunModel());
        this.shadowRadius = 0.2f;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public void render(PocketSunEntity entity, float entityYaw, float partialTick,
                       MatrixStack poseStack, VertexConsumerProvider bufferSource, int packedLight) {
        poseStack.push();

        // keep the same growth curve, just raise the base visual size
        float s = entity.getVisualScale() * VISUAL_SIZE_MULTIPLIER;
        poseStack.scale(s, s, s);

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        poseStack.pop();
    }
}