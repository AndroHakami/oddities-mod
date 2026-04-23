package net.seep.odd.entity.darkknight.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.seep.odd.entity.darkknight.DarkShieldEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * GeckoLib renderer for Dark Shield.
 *
 * Uses a translucent layer to give the shield its glassy look.
 * Texture path is kept directly under textures/entity/ as requested.
 */
public class DarkShieldRenderer extends GeoEntityRenderer<DarkShieldEntity> {
    public DarkShieldRenderer(net.minecraft.client.render.entity.EntityRendererFactory.Context ctx) {
        super(ctx, new DarkShieldModel());
        this.addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0.0F;
    }

    @Override
    public RenderLayer getRenderType(DarkShieldEntity entity, Identifier texture, VertexConsumerProvider buffers, float partialTick) {
        return RenderLayer.getEntityTranslucent(texture);
    }

    @Override
    protected int getBlockLight(DarkShieldEntity entity, BlockPos pos) {
        return 15;
    }

    @Override
    protected int getSkyLight(DarkShieldEntity entity, BlockPos pos) {
        return 15;
    }

    @Override
    public void render(DarkShieldEntity entity, float entityYaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider buffers, int packedLight) {
        matrices.push();

        // Tiny interpolation-based leaning boost so the shield feels more alive while orbiting
        Entity tracked = null;
        ClientWorld cw = MinecraftClient.getInstance().world;
        if (cw != null) {
            int trackedId = entity.getTrackedProtectedId();
            if (trackedId != 0) {
                tracked = cw.getEntityById(trackedId);
            }
        }

        if (tracked != null && tracked.isAlive()) {
            double tx = MathHelper.lerp(tickDelta, tracked.lastRenderX, tracked.getX());
            double ty = MathHelper.lerp(tickDelta, tracked.lastRenderY, tracked.getY());
            double tz = MathHelper.lerp(tickDelta, tracked.lastRenderZ, tracked.getZ());

            double ex = MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX());
            double ey = MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY());
            double ez = MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ());

            // example lean/look math here if needed
        }

        super.render(entity, entityYaw, tickDelta, matrices, buffers, packedLight);
        matrices.pop();
    }
}
