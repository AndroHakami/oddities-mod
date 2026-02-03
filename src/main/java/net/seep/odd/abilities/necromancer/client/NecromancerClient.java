// src/main/java/net/seep/odd/abilities/necromancer/client/NecromancerClient.java
package net.seep.odd.abilities.necromancer.client;

import com.mojang.blaze3d.systems.RenderSystem;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;

import org.joml.Matrix4f;

import net.seep.odd.abilities.necromancer.NecromancerNet;
import net.seep.odd.item.ModItems;

@Environment(EnvType.CLIENT)
public final class NecromancerClient {
    private NecromancerClient() {}

    private static boolean summonMode = false;
    private static Vec3d aimPos = null;

    private static boolean prevAttackDown = false;
    private static boolean prevUseDown = false;

    private static int inputIgnoreTicks = 0;      // avoids instant cast when mode turns on
    private static int localFailsafeTicks = 0;    // prevents being stuck client-side forever

    private static int castingSendTicker = 0;     // keepalive pacing
    private static int sendLockTicks = 0;         // prevents double-send spam

    private static final int AIM_RANGE = 32;

    // visuals
    private static final int RING_SEGS = 96;
    private static final int RUNE_COUNT = 28;

    private static final float OUTER_R = 1.85f;
    private static final float OUTER_W = 0.070f;
    private static final float INNER_R = 1.25f;
    private static final float INNER_W = 0.045f;

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc == null || mc.player == null || mc.world == null) return;

            boolean attackDown = mc.options.attackKey.isPressed();
            boolean useDown = mc.options.useKey.isPressed();

            if (!summonMode) {
                aimPos = null;
                prevAttackDown = attackDown;
                prevUseDown = useDown;
                castingSendTicker = 0;
                localFailsafeTicks = 0;
                sendLockTicks = 0;
                return;
            }

            // client failsafe: if something goes weird, exit after ~10s
            localFailsafeTicks++;
            if (localFailsafeTicks > 200) {
                exitModeClientSafe();
                prevAttackDown = attackDown;
                prevUseDown = useDown;
                return;
            }

            // Must be holding staff while in summon mode
            if (!holdingStaff(mc)) {
                exitModeClientSafe();
                prevAttackDown = attackDown;
                prevUseDown = useDown;
                return;
            }

            // keepalive to server (also used as guard safety)
            castingSendTicker++;
            if (castingSendTicker >= 5) { // every 5 ticks
                castingSendTicker = 0;
                NecromancerNet.sendCasting(true);
            }

            // update aim every tick (precise)
            aimPos = raycastToSurface(mc);

            if (inputIgnoreTicks > 0) inputIgnoreTicks--;
            if (sendLockTicks > 0) sendLockTicks--;

            // rising edges
            boolean attackPressed = attackDown && !prevAttackDown;
            boolean usePressed = useDown && !prevUseDown;

            // don’t allow both in same tick, don’t fire instantly when mode just opened
            if (sendLockTicks == 0 && inputIgnoreTicks == 0) {
                if (attackPressed ^ usePressed) {
                    if (aimPos != null) {
                        BlockPos bp = BlockPos.ofFloored(aimPos.x, aimPos.y, aimPos.z);
                        boolean skeleton = usePressed;
                        NecromancerNet.sendSummon(skeleton, bp);
                        // lock briefly so you can't spam packets in the same cast
                        sendLockTicks = 6;
                    }
                }
            }

            prevAttackDown = attackDown;
            prevUseDown = useDown;
        });

        WorldRenderEvents.LAST.register(ctx -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world == null || mc.player == null) return;
            if (!summonMode) return;
            if (aimPos == null) return;

            Vec3d cam = ctx.camera().getPos();
            MatrixStack matrices = ctx.matrixStack();

            float time = (mc.world.getTime() + ctx.tickDelta());
            int rgb = 0xFF00FF; // magenta
            int r = (rgb >> 16) & 255;
            int g = (rgb >> 8) & 255;
            int b = (rgb) & 255;

            matrices.push();
            matrices.translate(-cam.x, -cam.y, -cam.z);

            // ✅ always slightly above the surface
            matrices.push();
            matrices.translate(aimPos.x, aimPos.y + 0.03, aimPos.z);

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);

            drawSummonSigil(matrices, time, r, g, b);

            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();

            matrices.pop();
            matrices.pop();
        });
    }

    /** Called from S2C packet */
    public static void setSummonMode(boolean on) {
        if (on && !summonMode) {
            inputIgnoreTicks = 2;
            localFailsafeTicks = 0;
            castingSendTicker = 0;
            sendLockTicks = 0;
            // tell server immediately we're "in cast"
            NecromancerNet.sendCasting(true);
        }

        if (!on && summonMode) {
            // clear local state
            aimPos = null;
            inputIgnoreTicks = 0;
            localFailsafeTicks = 0;
            castingSendTicker = 0;
            sendLockTicks = 0;
        }

        summonMode = on;
    }

    public static boolean isSummonMode() {
        return summonMode;
    }

    private static void exitModeClientSafe() {
        // Always tell server to cancel / drop guard
        NecromancerNet.sendCasting(false);
        summonMode = false;
        aimPos = null;
        inputIgnoreTicks = 0;
        localFailsafeTicks = 0;
        castingSendTicker = 0;
        sendLockTicks = 0;
    }

    private static boolean holdingStaff(MinecraftClient mc) {
        return mc.player.getMainHandStack().getItem() == ModItems.NECROMANCER_STAFF
                || mc.player.getOffHandStack().getItem() == ModItems.NECROMANCER_STAFF;
    }

    private static Vec3d raycastToSurface(MinecraftClient mc) {
        Vec3d start = mc.player.getCameraPosVec(1.0f);
        Vec3d dir   = mc.player.getRotationVec(1.0f).normalize();
        Vec3d end   = start.add(dir.multiply(AIM_RANGE));

        HitResult hit = mc.world.raycast(new RaycastContext(
                start, end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mc.player
        ));

        if (hit.getType() == HitResult.Type.MISS) {
            // forward fallback
            Vec3d fwd = mc.player.getPos().add(dir.multiply(6.0));
            return new Vec3d(fwd.x, mc.player.getY(), fwd.z);
        }

        return hit.getPos();
    }

    /* ===================== Visuals ===================== */

    private static void drawSummonSigil(MatrixStack matrices, float time, int r, int g, int b) {
        float pulse = 0.88f + 0.12f * MathHelper.sin(time * 0.10f);
        int aOuter = clamp((int)(170f * pulse), 0, 220);
        int aInner = clamp((int)(120f * (0.85f + 0.15f * MathHelper.sin(time * 0.15f))), 0, 190);

        // filled disk (subtle)
        int aDisk = clamp((int)(28f * pulse), 0, 60);
        drawFilledDisk(matrices, INNER_R * 0.85f, r, g, b, aDisk);

        // double ring
        drawGroundRing(matrices, OUTER_R, OUTER_W, r, g, b, aOuter);
        drawGroundRing(matrices, INNER_R, INNER_W, r, g, b, aInner);

        // runes around outer ring
        float rot = (time * 2.25f) % 360f;
        drawRunes(matrices, OUTER_R - OUTER_W * 0.25f, r, g, b, clamp((int)(150f * pulse), 0, 220), rot);

        // animated “arc” sweep (makes it feel less static)
        float arcR = OUTER_R + 0.02f;
        drawArc(matrices, arcR, r, g, b, clamp((int)(120f * pulse), 0, 200), rot, 38f);
    }

    private static void drawRunes(MatrixStack matrices, float radius, int r, int g, int b, int a, float rotDeg) {
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        Matrix4f mat = matrices.peek().getPositionMatrix();

        float runeHalfW = Math.max(0.035f, radius * 0.013f);
        float runeHalfH = Math.max(0.050f, radius * 0.020f);

        buf.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        for (int i = 0; i < RUNE_COUNT; i++) {
            // sparse pattern so it looks “runic”
            int mask = (i * 17) & 7;
            if (mask == 0 || mask == 5) continue;

            double ang = (Math.PI * 2.0 * i) / (double)RUNE_COUNT + Math.toRadians(rotDeg * 0.25f);
            float c = (float)Math.cos(ang);
            float s = (float)Math.sin(ang);

            // tangent + radial
            float tx = -s, tz = c;
            float rx = c,  rz = s;

            float cx = rx * radius;
            float cz = rz * radius;

            float off = Math.max(0.01f, radius * 0.004f);
            float ox = rx * off;
            float oz = rz * off;

            float x0 = cx - tx * runeHalfW + ox;
            float z0 = cz - tz * runeHalfW + oz;
            float x1 = cx + tx * runeHalfW + ox;
            float z1 = cz + tz * runeHalfW + oz;

            // vertical “slab” rune
            vtx(buf, mat, x0, -runeHalfH, z0, r, g, b, a);
            vtx(buf, mat, x0,  runeHalfH, z0, r, g, b, a);
            vtx(buf, mat, x1,  runeHalfH, z1, r, g, b, a);

            vtx(buf, mat, x0, -runeHalfH, z0, r, g, b, a);
            vtx(buf, mat, x1,  runeHalfH, z1, r, g, b, a);
            vtx(buf, mat, x1, -runeHalfH, z1, r, g, b, a);
        }

        BufferRenderer.drawWithGlobalProgram(buf.end());
    }

    private static void drawArc(MatrixStack matrices, float radius, int r, int g, int b, int a, float rotDeg, float arcDeg) {
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        Matrix4f mat = matrices.peek().getPositionMatrix();

        float halfW = Math.max(0.03f, radius * 0.010f);

        double start = Math.toRadians(rotDeg);
        double end = start + Math.toRadians(arcDeg);

        int segs = 28;

        buf.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        for (int i = 0; i < segs; i++) {
            double t0 = start + (end - start) * (i / (double)segs);
            double t1 = start + (end - start) * ((i + 1) / (double)segs);

            float c0 = (float)Math.cos(t0), s0 = (float)Math.sin(t0);
            float c1 = (float)Math.cos(t1), s1 = (float)Math.sin(t1);

            float in0x = c0 * (radius - halfW),  in0z = s0 * (radius - halfW);
            float out0x = c0 * (radius + halfW), out0z = s0 * (radius + halfW);

            float in1x = c1 * (radius - halfW),  in1z = s1 * (radius - halfW);
            float out1x = c1 * (radius + halfW), out1z = s1 * (radius + halfW);

            vtx(buf, mat, in0x, 0f, in0z, r, g, b, a);
            vtx(buf, mat, out0x, 0f, out0z, r, g, b, a);
            vtx(buf, mat, out1x, 0f, out1z, r, g, b, a);

            vtx(buf, mat, in0x, 0f, in0z, r, g, b, a);
            vtx(buf, mat, out1x, 0f, out1z, r, g, b, a);
            vtx(buf, mat, in1x, 0f, in1z, r, g, b, a);
        }

        BufferRenderer.drawWithGlobalProgram(buf.end());
    }

    private static void drawGroundRing(MatrixStack matrices, float radius, float halfWidth,
                                       int r, int g, int b, int a) {
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

    private static void drawFilledDisk(MatrixStack matrices, float radius, int r, int g, int b, int a) {
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

    private static void vtx(BufferBuilder b, Matrix4f mat, float x, float y, float z, int r, int g, int bb, int a) {
        b.vertex(mat, x, y, z).color(r, g, bb, a).next();
    }

    private static int clamp(int v, int lo, int hi) {
        return MathHelper.clamp(v, lo, hi);
    }
}
