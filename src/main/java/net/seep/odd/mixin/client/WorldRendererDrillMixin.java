package net.seep.odd.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.*;
import net.seep.odd.abilities.lunar.client.LunarDrillPreview;
import net.seep.odd.abilities.lunar.item.LunarDrillItem;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Environment(EnvType.CLIENT)
@Mixin(WorldRenderer.class)
public class WorldRendererDrillMixin {

    /**
     * Denser = looks like it actually fills.
     * Uses lattice points on a (GRID_N+1)x(GRID_N+1) grid, so it includes u/v=0 and 1 exactly.
     *
     * 16 -> points at 0, 1/16, 2/16, ... , 1
     */
    private static final int GRID_N = 16;

    // Push lines slightly off faces to avoid z-fighting
    private static final double FACE_EPS = 0.003;

    private static final int SPIRAL_LEN = (GRID_N + 1) * (GRID_N + 1);
    private static final float[] SPIRAL_U = new float[SPIRAL_LEN];
    private static final float[] SPIRAL_V = new float[SPIRAL_LEN];

    static {
        buildSpiralUV(GRID_N, SPIRAL_U, SPIRAL_V);
    }

    @Inject(
            method = "render(Lnet/minecraft/client/util/math/MatrixStack;FJZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/GameRenderer;Lnet/minecraft/client/render/LightmapTextureManager;Lorg/joml/Matrix4f;)V",
            at = @At("TAIL")
    )
    private void odd$renderLunarPreviewHighlights(MatrixStack matrices,
                                                  float tickDelta,
                                                  long limitTime,
                                                  boolean renderBlockOutline,
                                                  Camera camera,
                                                  GameRenderer gameRenderer,
                                                  LightmapTextureManager lightmap,
                                                  Matrix4f projectionMatrix,
                                                  CallbackInfo ci) {

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || mc.player == null) return;

        List<BlockPos> targets = LunarDrillPreview.getTargets();
        if (targets.isEmpty()) return;

        ClientPlayerEntity p = mc.player;

        boolean holdingDrill =
                p.getMainHandStack().getItem() instanceof LunarDrillItem ||
                        p.getOffHandStack().getItem()  instanceof LunarDrillItem;
        if (!holdingDrill) return;

        float pct = LunarDrillPreview.getChargeProgress(); // 0..1

        Vec3d camPos = camera.getPos();
        float t = (mc.world.getTime() + tickDelta);
        float pulse01 = 0.5f + 0.5f * MathHelper.sin(t * 0.25f);

        // Yellow
        float r = 1.0f;
        float g = 0.95f;
        float b = 0.10f;

        // Make "done" unmistakably fully highlighted
        float fillA    = (0.10f + 0.10f * pulse01) + (0.70f * pct); // up to ~0.90
        float haloA    = (0.03f + 0.06f * pulse01) + (0.12f * pct);
        float outlineA = 0.55f + 0.45f * pct;

        VertexConsumerProvider.Immediate consumers = mc.getBufferBuilders().getEntityVertexConsumers();

        VertexConsumer fill = null;
        try { fill = consumers.getBuffer(RenderLayer.getDebugFilledBox()); } catch (Throwable ignored) {}
        VertexConsumer line = consumers.getBuffer(RenderLayer.getLines());

        matrices.push();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        // Thicker “snake” (driver support varies, but usually works in MC)
        RenderSystem.lineWidth(2.5f);

        double expand = 0.002;
        double haloExpand = 0.040;

        int maxSeg = SPIRAL_LEN - 1;
        int shown = MathHelper.clamp((int) Math.floor(pct * maxSeg), 0, maxSeg);

        // Slight head emphasis (still clean/blocky)
        float headBoost = 0.85f + 0.15f * pulse01;

        for (BlockPos pos : targets) {
            Box box = new Box(pos).expand(expand);

            // Full-block fill + halo
            if (fill != null) {
                WorldRenderer.drawBox(matrices, fill, box, r, g, b, fillA);
                Box halo = new Box(pos).expand(haloExpand);
                WorldRenderer.drawBox(matrices, fill, halo, r, g, b, haloA);
            }

            // Outline
            WorldRenderer.drawBox(matrices, line, box, r, g, b, outlineA);

            // Clean spiral snake across ALL faces
            if (shown > 0) {
                for (int face = 0; face < 6; face++) {
                    for (int i = 0; i < shown; i++) {
                        float u0 = SPIRAL_U[i];
                        float v0 = SPIRAL_V[i];
                        float u1 = SPIRAL_U[i + 1];
                        float v1 = SPIRAL_V[i + 1];

                        float a = (0.35f + 0.55f * pct);
                        if (i >= shown - 14) a *= headBoost; // slightly brighter head

                        Vec3d p0 = uvToFace(pos, face, u0, v0, FACE_EPS);
                        Vec3d p1 = uvToFace(pos, face, u1, v1, FACE_EPS);

                        lineSegment(matrices, line, p0, p1, r, g, b, a);
                    }
                }
            }
        }

        // restore defaults-ish
        RenderSystem.lineWidth(1.0f);

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        matrices.pop();
        consumers.draw();
    }

    /**
     * Spiral visits ALL lattice points on (n+1)x(n+1), starting at edges (0/1) and ending at center (0.5 for even n).
     * Integer coords 0..n map to u/v = coord/n.
     */
    private static void buildSpiralUV(int n, float[] outU, float[] outV) {
        int idx = 0;

        int left = 0, right = n;
        int top = 0, bottom = n;

        while (left <= right && top <= bottom) {
            for (int x = left; x <= right; x++) idx = putUV(n, x, top, outU, outV, idx);
            top++;

            for (int y = top; y <= bottom; y++) idx = putUV(n, right, y, outU, outV, idx);
            right--;

            if (top > bottom) break;

            for (int x = right; x >= left; x--) idx = putUV(n, x, bottom, outU, outV, idx);
            bottom--;

            if (left > right) break;

            for (int y = bottom; y >= top; y--) idx = putUV(n, left, y, outU, outV, idx);
            left++;
        }

        while (idx < (n + 1) * (n + 1)) {
            outU[idx] = 0.5f;
            outV[idx] = 0.5f;
            idx++;
        }
    }

    private static int putUV(int n, int x, int y, float[] outU, float[] outV, int idx) {
        outU[idx] = x / (float) n; // includes 0 and 1 exactly
        outV[idx] = y / (float) n;
        return idx + 1;
    }

    // face mapping: 0 north(z-), 1 south(z+), 2 west(x-), 3 east(x+), 4 up(y+), 5 down(y-)
    private static Vec3d uvToFace(BlockPos pos, int face, float u, float v, double eps) {
        double x0 = pos.getX();
        double y0 = pos.getY();
        double z0 = pos.getZ();
        double x1 = x0 + 1.0;
        double y1 = y0 + 1.0;
        double z1 = z0 + 1.0;

        return switch (face) {
            case 0 -> new Vec3d(x0 + u, y0 + v, z0 - eps);
            case 1 -> new Vec3d(x0 + u, y0 + v, z1 + eps);
            case 2 -> new Vec3d(x0 - eps, y0 + v, z0 + u);
            case 3 -> new Vec3d(x1 + eps, y0 + v, z0 + u);
            case 4 -> new Vec3d(x0 + u, y1 + eps, z0 + v);
            default -> new Vec3d(x0 + u, y0 - eps, z0 + v);
        };
    }

    private static void lineSegment(MatrixStack ms, VertexConsumer vc, Vec3d a, Vec3d b,
                                    float r, float g, float bcol, float alpha) {
        MatrixStack.Entry e = ms.peek();
        Matrix4f posMat = e.getPositionMatrix();
        Matrix3f nMat = e.getNormalMatrix();

        float dx = (float) (b.x - a.x);
        float dy = (float) (b.y - a.y);
        float dz = (float) (b.z - a.z);
        float len = MathHelper.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > 1e-6f) { dx /= len; dy /= len; dz /= len; }

        int ir = (int) (MathHelper.clamp(r, 0f, 1f) * 255f);
        int ig = (int) (MathHelper.clamp(g, 0f, 1f) * 255f);
        int ib = (int) (MathHelper.clamp(bcol, 0f, 1f) * 255f);
        int ia = (int) (MathHelper.clamp(alpha, 0f, 1f) * 255f);

        vc.vertex(posMat, (float) a.x, (float) a.y, (float) a.z).color(ir, ig, ib, ia).normal(nMat, dx, dy, dz).next();
        vc.vertex(posMat, (float) b.x, (float) b.y, (float) b.z).color(ir, ig, ib, ia).normal(nMat, dx, dy, dz).next();
    }
}
