package net.seep.odd.entity.bosswitch.client;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.entity.bosswitch.RottenSpikeEntity;

public final class RottenSpikeRenderer extends EntityRenderer<RottenSpikeEntity> {
    private static final Identifier DUMMY = new Identifier("minecraft", "textures/misc/white.png");

    public RottenSpikeRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0f;
    }

    @Override
    public void render(RottenSpikeEntity entity,
                       float yaw,
                       float tickDelta,
                       MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers,
                       int light) {

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        BlockRenderManager blockRenderer = client.getBlockRenderManager();

        float progress = entity.getChargeProgress(tickDelta);
        boolean launched = entity.isLaunchedVisual();

        Vec3d vel = entity.getVelocity();
        float dirYaw = 0.0f;
        if (vel.lengthSquared() > 1.0E-6) {
            dirYaw = (float) (Math.atan2(vel.x, vel.z) * (180.0D / Math.PI));
        }

        matrices.push();

        // rise up from underground during charge
        if (!launched) {
            float eased = progress * progress * (3.0f - 2.0f * progress);
            matrices.translate(0.0D, -2.25D * (1.0D - eased), 0.0D);
        }

        // face the sliding direction
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(dirYaw));

        if (launched) {
            float wobble = 2.0f * MathHelper.sin((entity.age + tickDelta) * 0.35f);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(wobble));
        }

        // ===== chunky local ground mass =====
        // bottom 3x3 foundation: uses deeper/subsurface blocks
        for (int gx = -1; gx <= 1; gx++) {
            for (int gz = -1; gz <= 1; gz++) {
                BlockState state = sampleState(entity, gx, -1, gz);
                renderPiece(blockRenderer, state, matrices, vertexConsumers, light, gx, 0.0f, gz, 1.0f);
            }
        }

        // mid chunky surface
        renderPiece(blockRenderer, sampleState(entity, -1, 0, -1), matrices, vertexConsumers, light, -1, 1.0f, -1, 0.98f);
        renderPiece(blockRenderer, sampleState(entity,  0, 0, -1), matrices, vertexConsumers, light,  0, 1.0f, -1, 1.00f);
        renderPiece(blockRenderer, sampleState(entity,  1, 0, -1), matrices, vertexConsumers, light,  1, 1.0f, -1, 0.94f);

        renderPiece(blockRenderer, sampleState(entity, -1, 0,  0), matrices, vertexConsumers, light, -1, 1.0f,  0, 1.00f);
        renderPiece(blockRenderer, sampleState(entity,  0, 0,  0), matrices, vertexConsumers, light,  0, 1.0f,  0, 1.02f);
        renderPiece(blockRenderer, sampleState(entity,  1, 0,  0), matrices, vertexConsumers, light,  1, 1.0f,  0, 0.96f);

        renderPiece(blockRenderer, sampleState(entity, -1, 0,  1), matrices, vertexConsumers, light, -1, 1.0f,  1, 0.92f);
        renderPiece(blockRenderer, sampleState(entity,  0, 0,  1), matrices, vertexConsumers, light,  0, 1.0f,  1, 0.98f);

        // top ridge / broken crest
        renderPiece(blockRenderer, sampleState(entity, 0, 0, 0), matrices, vertexConsumers, light,  0, 2.0f,  0, 0.92f);
        renderPiece(blockRenderer, sampleState(entity, 1, 0, 0), matrices, vertexConsumers, light,  1, 2.0f,  0, 0.72f);
        renderPiece(blockRenderer, sampleState(entity, 0, 0, 1), matrices, vertexConsumers, light,  0, 2.0f,  1, 0.68f);
        renderPiece(blockRenderer, sampleState(entity, -1, 0, 0), matrices, vertexConsumers, light, -1, 2.0f,  0, 0.64f);

        matrices.pop();

        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    private static BlockState sampleState(RottenSpikeEntity entity, int ox, int oy, int oz) {
        BlockPos origin = entity.getOriginBlockPos();
        BlockPos pos = origin.add(ox, oy, oz);

        BlockState state = entity.getWorld().getBlockState(pos);
        if (!state.isAir()) return state;

        state = entity.getWorld().getBlockState(pos.down());
        if (!state.isAir()) return state;

        state = entity.getWorld().getBlockState(pos.down(2));
        if (!state.isAir()) return state;

        return Blocks.DIRT.getDefaultState();
    }

    private static void renderPiece(BlockRenderManager blockRenderer,
                                    BlockState state,
                                    MatrixStack matrices,
                                    VertexConsumerProvider vertexConsumers,
                                    int light,
                                    int gx,
                                    float gy,
                                    int gz,
                                    float scale) {
        matrices.push();

        float inset = (1.0f - scale) * 0.5f;
        matrices.translate(gx - 0.5f + inset, gy, gz - 0.5f + inset);
        matrices.scale(scale, scale, scale);

        blockRenderer.renderBlockAsEntity(state, matrices, vertexConsumers, light, OverlayTexture.DEFAULT_UV);

        matrices.pop();
    }

    @Override
    public Identifier getTexture(RottenSpikeEntity entity) {
        return DUMMY;
    }
}