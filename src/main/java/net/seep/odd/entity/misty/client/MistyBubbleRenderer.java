// net/seep/odd/entity/misty/client/MistyBubbleRenderer.java
package net.seep.odd.entity.misty.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.entity.Entity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import net.seep.odd.entity.misty.MistyBubbleEntity;

public class MistyBubbleRenderer extends GeoEntityRenderer<MistyBubbleEntity> {
    // The bubble model was authored for a "player-sized" entity. Use these as references.
    private static final float REF_W   = 0.60f;   // player width
    private static final float REF_H   = 1.80f;   // player height
    private static final float PADDING = 1.15f;   // a little extra space around the target

    // If your model needed a base scale previously (e.g., 2.6f), keep it here:
    private static final float BASE_MODEL_SCALE = 1f; // set to 1.0f if you don't want extra scaling

    // Avoid silly extremes (slimes/dragons, tiny mobs)
    private static final float MIN_SCALE = 0.55f;
    private static final float MAX_SCALE = 4.00f;

    public MistyBubbleRenderer(net.minecraft.client.render.entity.EntityRendererFactory.Context ctx) {
        super(ctx, new MistyBubbleModel());
        this.addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0f;
    }

    @Override
    public RenderLayer getRenderType(MistyBubbleEntity e, Identifier tex,
                                     VertexConsumerProvider buffers, float partialTick) {
        return RenderLayer.getEntityTranslucent(tex);
    }

    @Override protected int getBlockLight(MistyBubbleEntity e, BlockPos pos) { return 15; }
    @Override protected int getSkyLight(MistyBubbleEntity e, BlockPos pos) { return 15; }

    @Override
    public void render(MistyBubbleEntity entity, float entityYaw, float tickDelta,
                       MatrixStack matrices, VertexConsumerProvider buffers, int packedLight) {
        // Base (entity's interpolated position)
        double bx = MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX());
        double by = MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY());
        double bz = MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ());

        // Host to follow (interpolated)
        Entity host = null;
        float yOff = entity.getTrackedYOffset();

        ClientWorld cw = MinecraftClient.getInstance().world;
        if (cw != null) {
            int id = entity.getTrackedTargetId();
            if (id != 0) host = cw.getEntityById(id);
        }

        matrices.push();
        if (host != null && host.isAlive()) {
            double hx = MathHelper.lerp(tickDelta, host.lastRenderX, host.getX());
            double hy = MathHelper.lerp(tickDelta, host.lastRenderY, host.getY()) + yOff;
            double hz = MathHelper.lerp(tickDelta, host.lastRenderZ, host.getZ());

            // Draw at the host's feet (smooth)
            matrices.translate(hx - bx, hy - by, hz - bz);

            // === Dynamic scale based on host size ===
            float w = host.getWidth();
            float h = host.getHeight();

            // Uniform scale that fits both width and height, with padding.
            float fitByH = (h <= 0f ? 1f : h / REF_H);
            float fitByW = (w <= 0f ? 1f : w / REF_W);
            float s = Math.max(fitByH, fitByW) * PADDING;

            // Clamp, and include any base model scale you used before.
            s = MathHelper.clamp(s, MIN_SCALE, MAX_SCALE) * BASE_MODEL_SCALE;

            matrices.scale(s, s, s);
        }
        // If no host, we render at entity position at default scale.

        super.render(entity, entityYaw, tickDelta, matrices, buffers, packedLight);
        matrices.pop();
    }
}
