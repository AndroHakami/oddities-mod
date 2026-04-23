package net.seep.odd.block.client;

import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.block.DabloonStoreBlock;
import net.seep.odd.block.DabloonStoreBlockEntity;
import net.seep.odd.client.fx.DabloonStoreSatinFx;

public final class DabloonStoreRenderer implements BlockEntityRenderer<DabloonStoreBlockEntity> {
    private static final float PANEL_Y = 0.61f;
    private static final float START_OUTWARD = 0.045f;
    private static final float MAX_EXTRA_OUTWARD = 0.16f;

    private static final double RING_BACK_SHIFT = 0.020D;
    private static final double ITEM_FORWARD = 0.010D;

    public DabloonStoreRenderer(BlockEntityRendererFactory.Context context) {
    }

    @Override
    public void render(DabloonStoreBlockEntity entity, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        float progress = entity.getHologramProgress(tickDelta);
        if (progress <= 0.001f) return;

        BlockState state = entity.getCachedState();
        if (!state.contains(DabloonStoreBlock.FACING)) return;

        Direction facing = state.get(DabloonStoreBlock.FACING);

        float eased = easeOutBack(progress);
        float outward = START_OUTWARD + (MAX_EXTRA_OUTWARD * eased);
        float baseScale = 0.84f + 0.16f * eased;

        float time = (entity.getWorld() != null ? entity.getWorld().getTime() : 0.0f) + tickDelta;
        float bob = (float) Math.sin(time * 0.13f) * 0.010f * progress;

        Vec3d normal = new Vec3d(facing.getOffsetX(), facing.getOffsetY(), facing.getOffsetZ());
        Vec3d basePos = new Vec3d(
                entity.getPos().getX() + 0.5D + facing.getOffsetX() * (0.5005D + outward),
                entity.getPos().getY() + PANEL_Y + bob,
                entity.getPos().getZ() + 0.5D + facing.getOffsetZ() * (0.5005D + outward)
        );

        Vec3d ringOrigin = basePos.add(normal.multiply(-RING_BACK_SHIFT));
        Vec3d itemPos = basePos.add(normal.multiply(ITEM_FORWARD));

        int color = entity.getHologramColor();
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        DabloonStoreSatinFx.submit(entity.getPos().asLong(), ringOrigin, normal, progress, r, g, b);

        ItemStack stack = entity.getDisplayHologramStack();
        if (stack.isEmpty()) {
            return;
        }

        DabloonStoreHologramRenderer.render(
                entity,
                stack,
                facing,
                itemPos,
                matrices,
                vertexConsumers,
                0.82f * baseScale,
                tickDelta,
                progress,
                r,
                g,
                b
        );
    }

    private static float easeOutBack(float x) {
        float c1 = 1.70158f;
        float c3 = c1 + 1.0f;
        float t = x - 1.0f;
        return 1.0f + c3 * t * t * t + c1 * t * t;
    }
}