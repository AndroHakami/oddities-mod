// net/seep/odd/abilities/tamer/client/TameBallRenderer.java
package net.seep.odd.abilities.tamer.client;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import net.seep.odd.abilities.tamer.projectile.TameBallEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class TameBallRenderer extends GeoEntityRenderer<TameBallEntity> {
    public TameBallRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new TameBallModel());
        this.shadowRadius = 0.15f;
    }

    @Override
    protected void applyRotations(TameBallEntity e, MatrixStack ms, float ageInTicks, float yaw, float partialTicks) {
        super.applyRotations(e, ms, ageInTicks, yaw, partialTicks);

        // 1) Face the ball along its movement direction (smoothed on the entity)
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-e.clientYawFace));

        // 2) Roll around local X by accumulated degrees
        ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(e.clientRollDeg));
    }
}
