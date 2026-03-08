// src/main/java/net/seep/odd/entity/zerosuit/client/ZeroSuitMissileRenderer.java
package net.seep.odd.entity.zerosuit.client;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.seep.odd.entity.zerosuit.ZeroSuitMissileEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

import java.util.HashMap;
import java.util.Map;

public final class ZeroSuitMissileRenderer extends GeoEntityRenderer<ZeroSuitMissileEntity> {

    // If the model faces backwards, set to 180f. If 90° off, set to +/-90f.
    private static final float MODEL_YAW_OFFSET_DEG = 0.0f;

    // Nose-biased pivot so turns feel like the nose leads.
    private static final float NOSE_PIVOT = 0.28f;

    private static final Map<Integer, Float> SMOOTH_ROLL = new HashMap<>();

    public ZeroSuitMissileRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new ZeroSuitMissileModel());
        this.shadowRadius = 0.25f;
        this.addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    private static float smoothRoll(ZeroSuitMissileEntity m) {
        int id = m.getId();
        float target = m.getRoll();
        Float cur = SMOOTH_ROLL.get(id);
        if (cur == null) cur = target;

        // slightly snappier without twitch
        cur = MathHelper.lerp(0.28f, cur, target);
        SMOOTH_ROLL.put(id, cur);

        // cleanup safety
        if (!m.isAlive() || m.isRemoved()) {
            SMOOTH_ROLL.remove(id);
        }

        return cur;
    }

    @Override
    public void render(ZeroSuitMissileEntity entity,
                       float entityYaw,
                       float tickDelta,
                       MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers,
                       int light) {

        // IMPORTANT:
        // Do NOT translate to a second “smoothed” position here.
        // Vanilla already renders at (lerp(prevPos, pos)).
        // Your missile entity now does client-side sim + server correction,
        // so any extra translation here will reintroduce “ghost/jitter”.
        super.render(entity, entityYaw, tickDelta, matrices, vertexConsumers, light);
    }

    @Override
    protected void applyRotations(ZeroSuitMissileEntity missile,
                                  MatrixStack matrices,
                                  float ageInTicks,
                                  float rotationYaw,
                                  float tickDelta) {

        // Vanilla-style interpolation (stable)
        float yaw   = MathHelper.lerpAngleDegrees(tickDelta, missile.prevYaw, missile.getYaw());
        float pitch = MathHelper.lerp(tickDelta, missile.prevPitch, missile.getPitch());
        float roll  = smoothRoll(missile);

        float yawDeg = yaw + MODEL_YAW_OFFSET_DEG;

        // Nose pivot
        matrices.translate(0.0, 0.0, -NOSE_PIVOT);

        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f - yawDeg));
        matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(pitch));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(roll));

        matrices.translate(0.0, 0.0, NOSE_PIVOT);
    }
}