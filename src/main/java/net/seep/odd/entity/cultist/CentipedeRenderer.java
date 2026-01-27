package net.seep.odd.entity.cultist;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class CentipedeRenderer extends GeoEntityRenderer<CentipedeEntity> {
    public CentipedeRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new CentipedeModel());
        this.shadowRadius = 0.25f;
    }

    @Override
    public void render(CentipedeEntity entity, float entityYaw, float partialTicks,
                       MatrixStack matrices, net.minecraft.client.render.VertexConsumerProvider buffers, int light) {

        // Simple “tilt” when climbing so it feels like it’s going up a wall
        if (entity.isClimbing()) {
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(60.0f));
        }

        super.render(entity, entityYaw, partialTicks, matrices, buffers, light);
    }
}
