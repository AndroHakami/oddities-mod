package net.seep.odd.block.supercooker.client;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.seep.odd.block.supercooker.SuperCookerBlock;
import net.seep.odd.block.supercooker.SuperCookerBlockEntity;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class SuperCookerRenderer extends GeoBlockRenderer<SuperCookerBlockEntity> {
    public SuperCookerRenderer() {
        super(new SuperCookerModel());
    }

    @Override
    public void render(SuperCookerBlockEntity be, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider buffers, int light, int overlay) {

        // Face rotation from blockstate
        Direction dir = be.getCachedState().get(SuperCookerBlock.FACING);
        float rot = -dir.asRotation();

        // Emergence: geo height is 18/16 = 1.125 blocks :contentReference[oaicite:5]{index=5}
        int t = be.getEmergeTicks();
        float p = Math.min(1f, (t + tickDelta) / 20f);
        float start = -(18f / 16f) - 0.05f;
        float offsetY = start * (1f - p);

        matrices.push();

        matrices.translate(0.5, 0.0, 0.5);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rot));
        matrices.translate(-0.5, 0.0, -0.5);

        matrices.translate(0.0, offsetY, 0.0);

        super.render(be, tickDelta, matrices, buffers, light, overlay);
        matrices.pop();
    }
}
