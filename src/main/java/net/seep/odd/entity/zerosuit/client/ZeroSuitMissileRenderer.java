package net.seep.odd.entity.zerosuit.client;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.entity.zerosuit.ZeroSuitMissileEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public final class ZeroSuitMissileRenderer extends GeoEntityRenderer<ZeroSuitMissileEntity> {

    private static final float MODEL_YAW_OFFSET_DEG = 0.0f;



    public ZeroSuitMissileRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new ZeroSuitMissileModel());
        this.shadowRadius = 0.25f;
        this.addRenderLayer(new AutoGlowingGeoLayer<>(this));

    }
    @Override
    protected void applyRotations(ZeroSuitMissileEntity missile,
                                  MatrixStack matrices,
                                  float ageInTicks,
                                  float rotationYaw,
                                  float partialTick) {

        // Use velocity direction for visuals (this is what you asked for)
        Vec3d v = missile.getVelocity();
        float yawDeg = rotationYaw;
        float pitchDeg = missile.getPitch();

        if (v.lengthSquared() > 1.0e-6) {
            // Minecraft yaw: 0 = south (+Z)
            yawDeg = (float)(MathHelper.atan2(v.z, v.x) * (180.0 / Math.PI)) - 90.0f;
            // Minecraft pitch: negative = up
            double xz = Math.sqrt(v.x * v.x + v.z * v.z);
            pitchDeg = (float)(-(MathHelper.atan2(v.y, xz) * (180.0 / Math.PI)));
        }

        yawDeg += MODEL_YAW_OFFSET_DEG;

        // Yaw (turn)
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f - yawDeg));
        // Pitch (up/down)
        matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(pitchDeg));
        // Roll (bank) - uses your tracked roll
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(missile.getRoll()));
    }

    @Override
    public void render(ZeroSuitMissileEntity entity,
                       float entityYaw,
                       float tickDelta,
                       MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers,
                       int light) {
        // Let GeckoLib do the actual render, we just override rotations above.
        super.render(entity, entityYaw, tickDelta, matrices, vertexConsumers, light);
    }


}

