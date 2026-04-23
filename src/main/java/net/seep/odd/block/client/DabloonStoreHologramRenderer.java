package net.seep.odd.block.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.block.DabloonStoreBlockEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public final class DabloonStoreHologramRenderer {
    private DabloonStoreHologramRenderer() {}

    public static void render(DabloonStoreBlockEntity entity,
                              ItemStack stack,
                              Direction facing,
                              Vec3d itemPos,
                              MatrixStack matrices,
                              VertexConsumerProvider vertexConsumers,
                              float renderScale,
                              float tickDelta,
                              float progress,
                              float r,
                              float g,
                              float b) {
        float time = (entity.getWorld() != null ? entity.getWorld().getTime() : 0.0f) + tickDelta;
        float sway = (float) Math.sin(time * 0.09f) * 1.8f * progress;

        matrices.push();
        matrices.translate(
                itemPos.x - entity.getPos().getX(),
                itemPos.y - entity.getPos().getY(),
                itemPos.z - entity.getPos().getZ()
        );

        // Upright and facing outward.
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-facing.asRotation()));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(sway));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-6.0f));
        matrices.scale(renderScale, renderScale, renderScale);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // Pale ghost pass.
        renderItemPass(stack, matrices, vertexConsumers, entity, 1.0f, 1.0f, 1.0f, 0.10f, 1.02f, 0.000f);

        // Main tinted hologram pass.
        renderItemPass(stack, matrices, vertexConsumers, entity, r, g, b, 0.58f, 1.00f, 0.001f);

        // Soft edge glow.
        renderItemPass(
                stack,
                matrices,
                vertexConsumers,
                entity,
                mixToWhite(r, 0.35f),
                mixToWhite(g, 0.35f),
                mixToWhite(b, 0.35f),
                0.22f,
                1.035f,
                0.002f
        );

        drawScanLines(vertexConsumers, matrices, r, g, b, progress, time);
        drawFrame(vertexConsumers, matrices, r, g, b, progress);

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
        matrices.pop();
    }

    private static void renderItemPass(ItemStack stack,
                                       MatrixStack matrices,
                                       VertexConsumerProvider vertexConsumers,
                                       DabloonStoreBlockEntity entity,
                                       float r,
                                       float g,
                                       float b,
                                       float alpha,
                                       float extraScale,
                                       float zOffset) {
        matrices.push();
        matrices.translate(0.0D, 0.0D, zOffset);
        matrices.scale(extraScale, extraScale, extraScale);

        RenderSystem.setShaderColor(r, g, b, alpha);

        MinecraftClient.getInstance().getItemRenderer().renderItem(
                stack,
                ModelTransformationMode.GUI,
                0xF000F0,
                OverlayTexture.DEFAULT_UV,
                matrices,
                vertexConsumers,
                entity.getWorld(),
                0
        );

        matrices.pop();
    }

    private static void drawScanLines(VertexConsumerProvider vertexConsumers,
                                      MatrixStack matrices,
                                      float r,
                                      float g,
                                      float b,
                                      float progress,
                                      float time) {
        VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getLines());
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f pos = entry.getPositionMatrix();
        Matrix3f normal = entry.getNormalMatrix();

        int rr = Math.round(r * 255.0f);
        int gg = Math.round(g * 255.0f);
        int bb = Math.round(b * 255.0f);

        float halfW = 0.24f;
        float halfH = 0.24f;

        for (int i = 0; i < 7; i++) {
            float t = i / 6.0f;
            float y = -halfH + t * (halfH * 2.0f);
            float pulse = 0.65f + 0.35f * (float) Math.sin(time * 0.35f + i * 0.75f);
            int alpha = Math.round((18.0f + 36.0f * pulse) * progress);

            vc.vertex(pos, -halfW, y, 0.006f)
                    .color(rr, gg, bb, alpha)
                    .normal(normal, 0.0f, 0.0f, 1.0f)
                    .next();
            vc.vertex(pos, halfW, y, 0.006f)
                    .color(rr, gg, bb, alpha)
                    .normal(normal, 0.0f, 0.0f, 1.0f)
                    .next();
        }
    }

    private static void drawFrame(VertexConsumerProvider vertexConsumers,
                                  MatrixStack matrices,
                                  float r,
                                  float g,
                                  float b,
                                  float progress) {
        VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getLines());
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f pos = entry.getPositionMatrix();
        Matrix3f normal = entry.getNormalMatrix();

        int rr = Math.round(mixToWhite(r, 0.25f) * 255.0f);
        int gg = Math.round(mixToWhite(g, 0.25f) * 255.0f);
        int bb = Math.round(mixToWhite(b, 0.25f) * 255.0f);
        int alpha = Math.round(54.0f * progress);

        float left = -0.245f;
        float right = 0.245f;
        float top = -0.245f;
        float bottom = 0.245f;
        float z = 0.007f;

        line(vc, pos, normal, left, top, z, right, top, z, rr, gg, bb, alpha);
        line(vc, pos, normal, right, top, z, right, bottom, z, rr, gg, bb, alpha);
        line(vc, pos, normal, right, bottom, z, left, bottom, z, rr, gg, bb, alpha);
        line(vc, pos, normal, left, bottom, z, left, top, z, rr, gg, bb, alpha);
    }

    private static void line(VertexConsumer vc,
                             Matrix4f pos,
                             Matrix3f normal,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             int r, int g, int b, int alpha) {
        vc.vertex(pos, x1, y1, z1)
                .color(r, g, b, alpha)
                .normal(normal, 0.0f, 0.0f, 1.0f)
                .next();
        vc.vertex(pos, x2, y2, z2)
                .color(r, g, b, alpha)
                .normal(normal, 0.0f, 0.0f, 1.0f)
                .next();
    }

    private static float mixToWhite(float value, float amount) {
        return value + (1.0f - value) * amount;
    }
}