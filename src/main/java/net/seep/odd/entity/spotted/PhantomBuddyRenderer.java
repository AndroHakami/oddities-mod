package net.seep.odd.entity.spotted;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public final class PhantomBuddyRenderer extends GeoEntityRenderer<PhantomBuddyEntity> {
    public PhantomBuddyRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new PhantomBuddyModel());
        this.addRenderLayer(new AutoGlowingGeoLayer<>(this)); // auto-detects *_glow.png
        this.shadowRadius = 0.35f; // small + cute
    }

    @Override
    public RenderLayer getRenderType(PhantomBuddyEntity animatable, Identifier texture, VertexConsumerProvider buffers, float partialTick) {
        // Slight translucency looks nice for a “phantom” vibe; ensure your texture has alpha
        return RenderLayer.getEntityTranslucent(texture);
    }

    @Override
    public void render(PhantomBuddyEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider buffers, int light) {
        // tiny floaty bob when moving
        if (entity.getVelocity().lengthSquared() > 0.001) {
            matrices.translate(0, Math.sin((entity.age + tickDelta) * 0.12) * 0.02, 0);
        }
        super.render(entity, yaw, tickDelta, matrices, buffers, light);
    }
}
