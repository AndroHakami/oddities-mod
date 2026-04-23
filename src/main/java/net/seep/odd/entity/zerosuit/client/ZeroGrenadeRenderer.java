package net.seep.odd.entity.zerosuit.client;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.seep.odd.entity.zerosuit.ZeroGrenadeEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public final class ZeroGrenadeRenderer extends GeoEntityRenderer<ZeroGrenadeEntity> {
    private static final float MODEL_YAW_OFFSET_DEG = 0.0f;

    public ZeroGrenadeRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new ZeroGrenadeModel());
        this.shadowRadius = 0.2f;
        this.addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    protected void applyRotations(ZeroGrenadeEntity grenade,
                                  MatrixStack matrices,
                                  float ageInTicks,
                                  float rotationYaw,
                                  float tickDelta) {
        float yaw = MathHelper.lerpAngleDegrees(tickDelta, grenade.prevYaw, grenade.getYaw()) + MODEL_YAW_OFFSET_DEG;
        float pitch = MathHelper.lerp(tickDelta, grenade.prevPitch, grenade.getPitch());

        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f - yaw));
        matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(pitch));

        if (!grenade.isStuck()) {
            float spin = (grenade.age + tickDelta) * 32.0f;
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(spin));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(spin * 0.55f));
        }
    }
}
