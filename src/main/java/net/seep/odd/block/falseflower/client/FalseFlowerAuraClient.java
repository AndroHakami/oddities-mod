// src/main/java/net/seep/odd/block/falseflower/client/FalseFlowerAuraClient.java
package net.seep.odd.block.falseflower.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.block.falseflower.FalseFlowerTracker;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Environment(EnvType.CLIENT)
public final class FalseFlowerAuraClient {
    private FalseFlowerAuraClient() {}

    // sphere quality (auras)
    private static final int STACKS = 14;
    private static final int SEGS   = 28;

    // ring quality
    private static final int RING_SEGS  = 84;
    private static final int RUNE_COUNT = 28;

    // animation progress: 0..1
    private static final Map<Integer, Float> ANIM = new HashMap<>();
    private static float lastTime = Float.NaN;

    // cache last-known visuals so we can animate OUT even if snapshot lags
    private static final Map<Integer, VisCache> CACHE = new HashMap<>();

    private static final float IN_SPEED  = 0.28f;
    private static final float OUT_SPEED = 0.22f;

    // how fast range changes ease (bigger = snappier)
    private static final float POWER_LERP = 0.18f;

    // when mana hits ~0, treat aura as off
    private static final float MANA_EPS = 0.001f;

    // one-shot flash (after activation)
    private static final float FLASH_DECAY_PER_TICK = 0.22f; // ~5 ticks of flash

    private static final class VisCache {
        BlockPos pos;
        double cx, cy, cz;

        int rgb;

        float targetPower;
        float smoothPower;

        boolean oneShot;
        String spellKey;

        float armProg;        // 0..1 from server snapshot
        int armDur;

        float lastArmProg;
        boolean shotComplete; // prevents looping
        float flash;          // 0..1 (visual burst after completion)

        VisCache(BlockPos pos, float power, int rgb, boolean oneShot, String spellKey, float armProg, int armDur) {
            this.pos = pos;
            this.cx = pos.getX() + 0.5;
            this.cy = pos.getY() + 0.5;
            this.cz = pos.getZ() + 0.5;
            this.rgb = rgb;

            this.targetPower = power;
            this.smoothPower = power;

            this.oneShot = oneShot;
            this.spellKey = (spellKey == null ? "" : spellKey);

            this.armProg = armProg;
            this.armDur = armDur;

            this.lastArmProg = armProg;
            this.shotComplete = false;
            this.flash = 0f;
        }

        void update(BlockPos pos, float power, int rgb, boolean oneShot, String spellKey, float armProg, int armDur) {
            this.pos = pos;
            this.cx = pos.getX() + 0.5;
            this.cy = pos.getY() + 0.5;
            this.cz = pos.getZ() + 0.5;

            this.rgb = rgb;
            this.targetPower = power;

            // if spell changes, reset one-shot state
            String sk = (spellKey == null ? "" : spellKey);
            if (!sk.equals(this.spellKey)) {
                this.spellKey = sk;
                this.shotComplete = false;
                this.flash = 0f;
                this.lastArmProg = armProg;
            }

            this.oneShot = oneShot;
            this.armDur = armDur;

            // detect restart (arm progress jumped backwards while active one-shot is still visible)
            if (this.oneShot && this.shotComplete && armProg < 0.08f && this.lastArmProg > 0.85f) {
                this.shotComplete = false;
                this.flash = 0f;
            }

            this.armProg = armProg;
            this.lastArmProg = armProg;
        }

        void tickSmooth(float dt) {
            float t = MathHelper.clamp(POWER_LERP * dt, 0f, 1f);
            this.smoothPower = MathHelper.lerp(t, this.smoothPower, this.targetPower);

            if (flash > 0f) {
                flash = Math.max(0f, flash - FLASH_DECAY_PER_TICK * dt);
            }
        }
    }

    public static void init() {
        WorldRenderEvents.LAST.register(ctx -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null) return;

            Vec3d cam = ctx.camera().getPos();
            MatrixStack matrices = ctx.matrixStack();

            float now = (client.world.getTime() + ctx.tickDelta());
            float dt = Float.isNaN(lastTime) ? 0f : Math.min(2f, now - lastTime);
            lastTime = now;

            matrices.push();
            matrices.translate(-cam.x, -cam.y, -cam.z);

            Set<Integer> seen = new HashSet<>();

            // ---- pass 1: update from snapshot ----
            for (var e : FalseFlowerTracker.clientSnapshot()) {
                int id = e.id();
                seen.add(id);

                BlockPos pos = e.pos();
                boolean blockGone = client.world.getBlockState(pos).isAir();

                int rgb = e.spellColorRgb() & 0x00FFFFFF;
                float power = Math.max(0.25f, e.power());

                CACHE.compute(id, (k, old) -> {
                    if (old == null) return new VisCache(pos, power, rgb, e.oneShot(), e.spellKey(), e.armProgress(), e.armDurationTicks());
                    old.update(pos, power, rgb, e.oneShot(), e.spellKey(), e.armProgress(), e.armDurationTicks());
                    return old;
                });

                VisCache vc = CACHE.get(id);
                if (vc == null) continue;
                vc.tickSmooth(dt);

                boolean baseOn = e.active() && e.mana() > MANA_EPS && e.power() > 0.01f && !blockGone;

                // one-shot: mark completion once (prevents looping)
                if (vc.oneShot && !vc.shotComplete && vc.armProg >= 0.999f) {
                    vc.shotComplete = true;
                    vc.flash = 1.0f; // burst
                }

                boolean shouldBeOn;
                if (!vc.oneShot) {
                    shouldBeOn = baseOn;
                } else {
                    shouldBeOn = (baseOn && !vc.shotComplete) || (vc.flash > 0f);
                }

                float p = ANIM.getOrDefault(id, shouldBeOn ? 1f : 0f);
                float target = shouldBeOn ? 1f : 0f;

                if (p < target) p = Math.min(target, p + IN_SPEED * dt);
                else if (p > target) p = Math.max(target, p - OUT_SPEED * dt);

                ANIM.put(id, p);

                if (p <= 0.001f) continue;

                double dist2 = cam.squaredDistanceTo(vc.cx, vc.cy, vc.cz);
                if (dist2 > 90.0 * 90.0) continue;

                float baseRadius = 6f * Math.max(0.25f, vc.smoothPower);

                matrices.push();
                matrices.translate(vc.cx, vc.cy, vc.cz);

                if (!vc.oneShot) {
                    drawBarrierSphere(matrices, cam, baseRadius, vc.rgb, now, p, id);
                } else {
                    drawOneShotSigil(matrices, baseRadius, vc.rgb, now, p, id, vc);
                }

                matrices.pop();
            }

            // ---- pass 2: animate OUT using cache for missing entries ----
            for (var it = ANIM.entrySet().iterator(); it.hasNext(); ) {
                var en = it.next();
                int id = en.getKey();

                if (seen.contains(id)) continue;

                float p = en.getValue();
                if (p <= 0.001f) {
                    it.remove();
                    CACHE.remove(id);
                    continue;
                }

                p = Math.max(0f, p - OUT_SPEED * dt);
                en.setValue(p);

                VisCache vc = CACHE.get(id);
                if (vc == null || p <= 0.001f) continue;

                vc.tickSmooth(dt);

                double dist2 = cam.squaredDistanceTo(vc.cx, vc.cy, vc.cz);
                if (dist2 > 90.0 * 90.0) continue;

                float baseRadius = 6f * Math.max(0.25f, vc.smoothPower);

                matrices.push();
                matrices.translate(vc.cx, vc.cy, vc.cz);

                if (!vc.oneShot) {
                    drawBarrierSphere(matrices, cam, baseRadius, vc.rgb, now, p, id);
                } else {
                    drawOneShotSigil(matrices, baseRadius, vc.rgb, now, p, id, vc);
                }

                matrices.pop();
            }

            matrices.pop();
        });
    }

    /* ==================== AURAS (sphere) ==================== */

    private static void drawBarrierSphere(MatrixStack matrices, Vec3d camPos,
                                          float baseRadius, int rgb, float time, float alphaMul, int idSeed) {
        if (baseRadius <= 0.01f || alphaMul <= 0.001f) return;

        float ease = 1f - (float) Math.pow(1f - alphaMul, 3.2);
        float radius = baseRadius * (0.12f + 0.88f * ease);
        float wobble = (1f - ease) * 0.08f * MathHelper.sin(time * 0.85f + idSeed * 0.21f);
        radius *= (1f + wobble);

        int r = (rgb >> 16) & 255;
        int g = (rgb >> 8) & 255;
        int b = (rgb) & 255;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        drawSphereLayer(matrices, camPos, radius * 1.0025f, r, g, b, time, alphaMul);
        drawSphereLayer(matrices, camPos, radius * 0.9875f, r, g, b, time, alphaMul);

        float ringR = radius * 1.02f;
        drawRuneRing(matrices, ringR, r, g, b, time, alphaMul, idSeed,
                +1f, 1.00f, 18f, 6f);
        drawRuneRing(matrices, ringR, r, g, b, time, alphaMul, idSeed,
                -1f, 0.78f, -18f, 6f);

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void drawSphereLayer(MatrixStack matrices, Vec3d camPos,
                                        float radius, int r, int g, int b,
                                        float time, float alphaMul) {
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        Matrix4f mat = matrices.peek().getPositionMatrix();

        buf.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        float pulse = 0.88f + 0.12f * MathHelper.sin(time * 0.08f);

        for (int i = 0; i < STACKS; i++) {
            double v0 = (double) i / (double) STACKS;
            double v1 = (double) (i + 1) / (double) STACKS;

            double phi0 = (v0 - 0.5) * Math.PI;
            double phi1 = (v1 - 0.5) * Math.PI;

            double y0 = Math.sin(phi0);
            double y1 = Math.sin(phi1);

            double cr0 = Math.cos(phi0);
            double cr1 = Math.cos(phi1);

            for (int j = 0; j < SEGS; j++) {
                double u0 = (double) j / (double) SEGS;
                double u1 = (double) (j + 1) / (double) SEGS;

                double th0 = u0 * Math.PI * 2.0;
                double th1 = u1 * Math.PI * 2.0;

                float x00 = (float) (Math.cos(th0) * cr0);
                float z00 = (float) (Math.sin(th0) * cr0);
                float y00 = (float) (y0);

                float x10 = (float) (Math.cos(th1) * cr0);
                float z10 = (float) (Math.sin(th1) * cr0);
                float y10 = y00;

                float x01 = (float) (Math.cos(th0) * cr1);
                float z01 = (float) (Math.sin(th0) * cr1);
                float y01 = (float) (y1);

                float x11 = (float) (Math.cos(th1) * cr1);
                float z11 = (float) (Math.sin(th1) * cr1);
                float y11 = y01;

                vtxSphere(buf, mat, camPos, x00, y00, z00, radius, r, g, b, pulse, time, alphaMul);
                vtxSphere(buf, mat, camPos, x01, y01, z01, radius, r, g, b, pulse, time, alphaMul);
                vtxSphere(buf, mat, camPos, x11, y11, z11, radius, r, g, b, pulse, time, alphaMul);

                vtxSphere(buf, mat, camPos, x00, y00, z00, radius, r, g, b, pulse, time, alphaMul);
                vtxSphere(buf, mat, camPos, x11, y11, z11, radius, r, g, b, pulse, time, alphaMul);
                vtxSphere(buf, mat, camPos, x10, y10, z10, radius, r, g, b, pulse, time, alphaMul);
            }
        }

        BufferRenderer.drawWithGlobalProgram(buf.end());
    }

    private static void vtxSphere(BufferBuilder b, Matrix4f mat, Vec3d camPos,
                                  float nx, float ny, float nz, float radius,
                                  int r, int g, int bb,
                                  float pulse, float time, float alphaMul) {

        float px = nx * radius;
        float py = ny * radius;
        float pz = nz * radius;

        double vx = camPos.x - px;
        double vy = camPos.y - py;
        double vz = camPos.z - pz;
        double len = Math.sqrt(vx * vx + vy * vy + vz * vz) + 1e-6;

        vx /= len; vy /= len; vz /= len;

        double ndv = Math.abs(nx * vx + ny * vy + nz * vz);
        double fresnel = Math.pow(1.0 - MathHelper.clamp((float) ndv, 0f, 1f), 2.2);

        float shimmer = 0.92f + 0.08f * MathHelper.sin(time * 0.15f + (nx * 3.1f + nz * 2.7f));

        float a = (float) ((18.0 + fresnel * 85.0) * pulse * shimmer);
        a *= alphaMul;

        int alpha = clampAlpha((int) a, 0, 140);
        b.vertex(mat, px, py, pz).color(r, g, bb, alpha).next();
    }

    private static void drawRuneRing(MatrixStack matrices,
                                     float ringRadius, int r, int g, int b,
                                     float time, float alphaMul, int idSeed,
                                     float dir, float speedMul, float baseTiltDeg, float tiltWaveDeg) {
        if (ringRadius <= 0.02f || alphaMul <= 0.001f) return;

        float pulse = 0.90f + 0.10f * MathHelper.sin(time * 0.10f);
        float shimmer = 0.92f + 0.08f * MathHelper.sin(time * 0.17f + idSeed * 0.3f);

        float bandHalf = Math.max(0.035f, ringRadius * 0.018f);
        float runeHalfW = Math.max(0.035f, ringRadius * 0.014f);
        float runeHalfH = Math.max(0.045f, ringRadius * 0.020f);

        matrices.push();

        float rotY = (time * 2.25f * speedMul * dir) + (idSeed * 11.3f);
        float tilt = baseTiltDeg + tiltWaveDeg * MathHelper.sin(time * 0.05f + idSeed * 0.7f);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotY));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(tilt));

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        Matrix4f mat = matrices.peek().getPositionMatrix();

        buf.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        for (int i = 0; i < RING_SEGS; i++) {
            double t0 = (Math.PI * 2.0 * i) / RING_SEGS;
            double t1 = (Math.PI * 2.0 * (i + 1)) / RING_SEGS;

            float c0 = (float) Math.cos(t0), s0 = (float) Math.sin(t0);
            float c1 = (float) Math.cos(t1), s1 = (float) Math.sin(t1);

            float in0x = c0 * (ringRadius - bandHalf);
            float in0z = s0 * (ringRadius - bandHalf);
            float out0x = c0 * (ringRadius + bandHalf);
            float out0z = s0 * (ringRadius + bandHalf);

            float in1x = c1 * (ringRadius - bandHalf);
            float in1z = s1 * (ringRadius - bandHalf);
            float out1x = c1 * (ringRadius + bandHalf);
            float out1z = s1 * (ringRadius + bandHalf);

            int aBand = clampAlpha((int) (30f * alphaMul * pulse * shimmer), 0, 120);

            vtx(buf, mat, in0x, 0f, in0z, r, g, b, aBand);
            vtx(buf, mat, out0x, 0f, out0z, r, g, b, aBand);
            vtx(buf, mat, out1x, 0f, out1z, r, g, b, aBand);

            vtx(buf, mat, in0x, 0f, in0z, r, g, b, aBand);
            vtx(buf, mat, out1x, 0f, out1z, r, g, b, aBand);
            vtx(buf, mat, in1x, 0f, in1z, r, g, b, aBand);
        }

        for (int i = 0; i < RUNE_COUNT; i++) {
            double base = (Math.PI * 2.0 * i) / RUNE_COUNT;

            int mask = (i * 17 + idSeed * 13) & 7;
            if (mask == 0 || mask == 5) continue;

            float c = (float) Math.cos(base);
            float s = (float) Math.sin(base);

            float tx = -s, tz = c;
            float rx = c,  rz = s;

            float cxr = c * (ringRadius + bandHalf * 0.55f);
            float czr = s * (ringRadius + bandHalf * 0.55f);

            float flick = 0.70f + 0.30f * MathHelper.sin(time * 0.22f + i * 1.9f + idSeed * 0.3f);
            int aRune = clampAlpha((int) (95f * alphaMul * pulse * flick), 0, 170);

            float hw = runeHalfW;
            float hh = runeHalfH;

            float off = Math.max(0.01f, ringRadius * 0.004f);
            float ox = rx * off;
            float oz = rz * off;

            float x0 = cxr - tx * hw + ox;
            float z0 = czr - tz * hw + oz;
            float x1 = cxr + tx * hw + ox;
            float z1 = czr + tz * hw + oz;

            vtx(buf, mat, x0, -hh, z0, r, g, b, aRune);
            vtx(buf, mat, x0,  hh, z0, r, g, b, aRune);
            vtx(buf, mat, x1,  hh, z1, r, g, b, aRune);

            vtx(buf, mat, x0, -hh, z0, r, g, b, aRune);
            vtx(buf, mat, x1,  hh, z1, r, g, b, aRune);
            vtx(buf, mat, x1, -hh, z1, r, g, b, aRune);
        }

        BufferRenderer.drawWithGlobalProgram(buf.end());
        matrices.pop();
    }

    /* ==================== ONE-SHOTS (sigil) ==================== */

    private static void drawOneShotSigil(MatrixStack matrices,
                                         float baseRadius, int rgb, float time,
                                         float alphaMul, int idSeed, VisCache vc) {
        if (baseRadius <= 0.02f || alphaMul <= 0.001f) return;

        int r = (rgb >> 16) & 255;
        int g = (rgb >> 8) & 255;
        int b = (rgb) & 255;

        // ✅ IMPORTANT: exactly 1 push / 1 pop (prevents MatrixStack underflow crash)
        matrices.push();
        // sit slightly above the flower base to avoid z-fighting
        matrices.translate(0.0, -0.49, 0.0);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        float outer = baseRadius;

        float pulse = 0.92f + 0.08f * MathHelper.sin(time * 0.12f + idSeed * 0.13f);
        int aOuter = clampAlpha((int) (180f * alphaMul * pulse), 0, 230);
        drawGroundRing(matrices, outer, Math.max(0.06f, outer * 0.018f), r, g, b, aOuter);

        float prog = MathHelper.clamp(vc.armProg, 0f, 1f);
        if (vc.shotComplete) prog = 1f;

        float fillR = outer * prog;
        int aFill = clampAlpha((int) (130f * alphaMul * (0.55f + 0.45f * pulse)), 0, 210);
        drawFilledDisk(matrices, fillR, r, g, b, aFill);

        if (prog > 0.03f && prog < 0.999f) {
            float wave = fillR;
            int aWave = clampAlpha((int) (210f * alphaMul), 0, 240);
            drawGroundRing(matrices, wave, Math.max(0.05f, outer * 0.012f), r, g, b, aWave);
        }

        if (vc.flash > 0f) {
            float t = 1f - vc.flash;
            float burstR = outer * (1.02f + 0.55f * t);
            int aBurst = clampAlpha((int) (260f * alphaMul * vc.flash), 0, 255);
            drawGroundRing(matrices, burstR, Math.max(0.10f, outer * 0.030f), r, g, b, aBurst);

            int aBurstFill = clampAlpha((int) (180f * alphaMul * vc.flash), 0, 255);
            drawFilledDisk(matrices, outer * (0.25f + 0.95f * t), r, g, b, aBurstFill);
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        matrices.pop();
    }

    private static void drawGroundRing(MatrixStack matrices, float radius, float halfWidth,
                                       int r, int g, int b, int a) {
        if (radius <= 0.02f || a <= 0) return;

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        Matrix4f mat = matrices.peek().getPositionMatrix();

        float inR = Math.max(0.001f, radius - halfWidth);
        float outR = radius + halfWidth;

        buf.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        for (int i = 0; i < RING_SEGS; i++) {
            double t0 = (Math.PI * 2.0 * i) / RING_SEGS;
            double t1 = (Math.PI * 2.0 * (i + 1)) / RING_SEGS;

            float c0 = (float) Math.cos(t0), s0 = (float) Math.sin(t0);
            float c1 = (float) Math.cos(t1), s1 = (float) Math.sin(t1);

            float in0x = c0 * inR,  in0z = s0 * inR;
            float out0x = c0 * outR, out0z = s0 * outR;

            float in1x = c1 * inR,  in1z = s1 * inR;
            float out1x = c1 * outR, out1z = s1 * outR;

            vtx(buf, mat, in0x, 0f, in0z, r, g, b, a);
            vtx(buf, mat, out0x, 0f, out0z, r, g, b, a);
            vtx(buf, mat, out1x, 0f, out1z, r, g, b, a);

            vtx(buf, mat, in0x, 0f, in0z, r, g, b, a);
            vtx(buf, mat, out1x, 0f, out1z, r, g, b, a);
            vtx(buf, mat, in1x, 0f, in1z, r, g, b, a);
        }

        BufferRenderer.drawWithGlobalProgram(buf.end());
    }

    private static void drawFilledDisk(MatrixStack matrices, float radius,
                                       int r, int g, int b, int a) {
        if (radius <= 0.02f || a <= 0) return;

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        Matrix4f mat = matrices.peek().getPositionMatrix();

        buf.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        for (int i = 0; i < RING_SEGS; i++) {
            double t0 = (Math.PI * 2.0 * i) / RING_SEGS;
            double t1 = (Math.PI * 2.0 * (i + 1)) / RING_SEGS;

            float x0 = (float) Math.cos(t0) * radius;
            float z0 = (float) Math.sin(t0) * radius;

            float x1 = (float) Math.cos(t1) * radius;
            float z1 = (float) Math.sin(t1) * radius;

            vtx(buf, mat, 0f, 0f, 0f, r, g, b, a);
            vtx(buf, mat, x0, 0f, z0, r, g, b, a);
            vtx(buf, mat, x1, 0f, z1, r, g, b, a);
        }

        BufferRenderer.drawWithGlobalProgram(buf.end());
    }

    /* ==================== common ==================== */

    private static void vtx(BufferBuilder b, Matrix4f mat,
                            float x, float y, float z,
                            int r, int g, int bb, int a) {
        b.vertex(mat, x, y, z).color(r, g, bb, a).next();
    }

    private static int clampAlpha(int a, int lo, int hi) {
        return MathHelper.clamp(a, lo, hi);
    }
}
