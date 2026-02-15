package net.seep.odd.block.gate.client;

import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import net.seep.odd.block.gate.DimensionalGateBlockEntity;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class DimensionalGateRenderer extends GeoBlockRenderer<DimensionalGateBlockEntity> {
    public DimensionalGateRenderer(BlockEntityRendererFactory.Context ctx) {
        super(new DimensionalGateGeoModel());
    }

    @Override
    protected void rotateBlock(Direction facing, MatrixStack poseStack) {
        super.rotateBlock(facing, poseStack);

        // IMPORTANT:
        // Controller block is at the "left-bottom" of the 4-wide structure,
        // but your model is centered. This nudges it right by 1.5 blocks to center it.
        //
        // If it offsets the wrong way in-game, change 1.5 -> -1.5.
        poseStack.translate(1.5, 0.0, 0.0);
    }
}
