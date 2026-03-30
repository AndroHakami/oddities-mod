package net.seep.odd.block.client;

import net.minecraft.block.BlockState;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.Oddities;
import net.seep.odd.block.DabloonsMachineBlock;
import net.seep.odd.block.DabloonsMachineBlockEntity;
import net.seep.odd.client.fx.DabloonsMachineSatinFx;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public final class DabloonsMachineRenderer implements BlockEntityRenderer<DabloonsMachineBlockEntity> {
    private static final Identifier HOLOGRAM_TEXTURE =
            new Identifier(Oddities.MOD_ID, "textures/block/dabloons_machine_hologram.png");

    private static final float PANEL_WIDTH = 0.72f;
    private static final float PANEL_HEIGHT = 0.72f;
    private static final float PANEL_Y = 0.60f;

    private static final float START_OUTWARD = 0.03f;
    private static final float MAX_EXTRA_OUTWARD = 0.20f;

    private static final double ICON_FORWARD = 0.020D;
    private static final double RING_BACK_SHIFT = 0.026D;

    public DabloonsMachineRenderer(BlockEntityRendererFactory.Context ctx) {
    }

    @Override
    public void render(DabloonsMachineBlockEntity entity, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        float progress = entity.getHologramProgress(tickDelta);
        if (progress <= 0.001f) return;

        BlockState state = entity.getCachedState();
        if (!state.contains(DabloonsMachineBlock.FACING)) return;

        Direction facing = state.get(DabloonsMachineBlock.FACING);

        float eased = easeOutBack(progress);
        float baseScale = 0.12f + (0.88f * eased);
        float outward = START_OUTWARD + (MAX_EXTRA_OUTWARD * eased);

        float time = (entity.getWorld() != null ? entity.getWorld().getTime() : 0) + tickDelta;
        float bob = ((float) Math.sin(time * 0.14f) * 0.0080f) * progress;
        float pulse = 1.0f + 0.004f * (float) Math.sin(time * 0.22f);

        Vec3d normal = new Vec3d(facing.getOffsetX(), facing.getOffsetY(), facing.getOffsetZ());

        Vec3d basePos = new Vec3d(
                entity.getPos().getX() + 0.5D + facing.getOffsetX() * (0.5005D + outward),
                entity.getPos().getY() + PANEL_Y + bob,
                entity.getPos().getZ() + 0.5D + facing.getOffsetZ() * (0.5005D + outward)
        );

        Vec3d ringOrigin = basePos.add(normal.multiply(-RING_BACK_SHIFT));
        Vec3d iconPos = basePos.add(normal.multiply(ICON_FORWARD));

        DabloonsMachineSatinFx.submit(entity.getPos().asLong(), ringOrigin, normal, progress);

        matrices.push();
        matrices.translate(
                iconPos.x - entity.getPos().getX(),
                iconPos.y - entity.getPos().getY(),
                iconPos.z - entity.getPos().getZ()
        );
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-facing.asRotation()));
        matrices.scale(PANEL_WIDTH * baseScale * pulse, PANEL_HEIGHT * baseScale * pulse, 1.0f);

        MatrixStack.Entry entry = matrices.peek();
        Matrix4f posMat = entry.getPositionMatrix();
        Matrix3f normalMat = entry.getNormalMatrix();

        VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(HOLOGRAM_TEXTURE));
        int fullBright = 0xF000F0;
        int iconAlpha = (int) (MathHelper.clamp(progress, 0.0f, 1.0f) * 255.0f);

        // brighter / less saturated than before
        renderDoubleSidedQuad(
                vc,
                posMat,
                normalMat,
                182, 250, 255, iconAlpha,
                fullBright,
                OverlayTexture.DEFAULT_UV
        );

        matrices.pop();
    }

    private static void renderDoubleSidedQuad(VertexConsumer vc, Matrix4f pos, Matrix3f normal,
                                              int r, int g, int b, int alpha, int light, int overlay) {
        put(vc, pos, normal, -0.5f, -0.5f, 0.0f, 0.0f, 1.0f, r, g, b, alpha, light, overlay, 0f, 0f, 1f);
        put(vc, pos, normal,  0.5f, -0.5f, 0.0f, 1.0f, 1.0f, r, g, b, alpha, light, overlay, 0f, 0f, 1f);
        put(vc, pos, normal,  0.5f,  0.5f, 0.0f, 1.0f, 0.0f, r, g, b, alpha, light, overlay, 0f, 0f, 1f);
        put(vc, pos, normal, -0.5f,  0.5f, 0.0f, 0.0f, 0.0f, r, g, b, alpha, light, overlay, 0f, 0f, 1f);

        put(vc, pos, normal, -0.5f,  0.5f, 0.0f, 0.0f, 0.0f, r, g, b, alpha, light, overlay, 0f, 0f, -1f);
        put(vc, pos, normal,  0.5f,  0.5f, 0.0f, 1.0f, 0.0f, r, g, b, alpha, light, overlay, 0f, 0f, -1f);
        put(vc, pos, normal,  0.5f, -0.5f, 0.0f, 1.0f, 1.0f, r, g, b, alpha, light, overlay, 0f, 0f, -1f);
        put(vc, pos, normal, -0.5f, -0.5f, 0.0f, 0.0f, 1.0f, r, g, b, alpha, light, overlay, 0f, 0f, -1f);
    }

    private static void put(VertexConsumer vc, Matrix4f pos, Matrix3f normal,
                            float x, float y, float z, float u, float v,
                            int r, int g, int b, int alpha, int light, int overlay,
                            float nx, float ny, float nz) {
        vc.vertex(pos, x, y, z)
                .color(r, g, b, alpha)
                .texture(u, v)
                .overlay(overlay)
                .light(light)
                .normal(normal, nx, ny, nz)
                .next();
    }

    private static float easeOutBack(float x) {
        float c1 = 1.70158f;
        float c3 = c1 + 1.0f;
        float t = x - 1.0f;
        return 1.0f + c3 * t * t * t + c1 * t * t;
    }
}