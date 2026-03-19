package net.seep.odd.entity.bosswitch.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.seep.odd.entity.bosswitch.BossGolemEntity;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Environment(EnvType.CLIENT)
public final class BossGolemShockwaveClient {
    private BossGolemShockwaveClient() {}

    private static final List<Pulse> PULSES = new ArrayList<>();
    private static float lastTime = Float.NaN;

    private static final class Pulse {
        final double x, y, z;
        final double baseSurfaceY;
        final float startTime;
        final boolean heavy;
        final int seed;

        Pulse(double x, double y, double z, double baseSurfaceY, float startTime, boolean heavy, int seed) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.baseSurfaceY = baseSurfaceY;
            this.startTime = startTime;
            this.heavy = heavy;
            this.seed = seed;
        }

        float age(float now) {
            return now - startTime;
        }

        float speed() {
            return heavy ? 1.85f : 1.45f;
        }

        float radius(float now) {
            return age(now) * speed();
        }

        float maxRadius() {
            return 30.0f;
        }
    }

    private static final class GroundInfo {
        final double y;
        final int rgb;

        GroundInfo(double y, int rgb) {
            this.y = y;
            this.rgb = rgb;
        }
    }

    private static final class WavePoint {
        final float x;
        final float y;
        final float z;
        final int rgb;

        WavePoint(float x, float y, float z, int rgb) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.rgb = rgb;
        }
    }

    public static void init() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            PULSES.clear();
            lastTime = Float.NaN;
        });

        WorldRenderEvents.LAST.register(ctx -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null) return;
            if (PULSES.isEmpty()) return;

            float now = client.world.getTime() + ctx.tickDelta();
            float dt = Float.isNaN(lastTime) ? 0f : Math.min(2.0f, now - lastTime);
            lastTime = now;

            Vec3d cam = ctx.camera().getPos();
            MatrixStack matrices = ctx.matrixStack();

            matrices.push();
            matrices.translate(-cam.x, -cam.y, -cam.z);

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);

            Tessellator tess = Tessellator.getInstance();
            BufferBuilder buf = tess.getBuffer();
            buf.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

            Iterator<Pulse> it = PULSES.iterator();
            while (it.hasNext()) {
                Pulse pulse = it.next();

                float radius = pulse.radius(now);
                if (radius > pulse.maxRadius()) {
                    it.remove();
                    continue;
                }

                double dist2 = cam.squaredDistanceTo(pulse.x, pulse.y, pulse.z);
                if (dist2 > 110.0 * 110.0) continue;

                drawPulse(buf, matrices.peek().getPositionMatrix(), client.world, pulse, now, dt);
            }

            BufferRenderer.drawWithGlobalProgram(buf.end());

            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();

            matrices.pop();
        });
    }

    public static void spawnWave(BossGolemEntity golem, boolean heavy) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        GroundInfo g = sampleGround(client.world, golem.getX(), golem.getY() + 1.0D, golem.getZ());
        double baseSurfaceY = g != null ? g.y : golem.getY();

        PULSES.add(new Pulse(
                golem.getX(),
                golem.getY(),
                golem.getZ(),
                baseSurfaceY,
                client.world.getTime(),
                heavy,
                golem.getId() * 31 + (heavy ? 7 : 3)
        ));
    }

    private static void drawPulse(BufferBuilder buf, Matrix4f mat, World world, Pulse pulse, float now, float dt) {
        float radius = pulse.radius(now);
        float maxRadius = pulse.maxRadius();

        int segs = MathHelper.clamp((int) (radius * 7.0f), 48, pulse.heavy ? 180 : 150);

        List<WavePoint> points = new ArrayList<>(segs + 1);
        for (int i = 0; i <= segs; i++) {
            double ang = (Math.PI * 2.0 * i) / segs;
            points.add(traceWaveSurface(world, pulse, ang, radius));
        }

        float life = 1.0f - (radius / maxRadius);
        float baseHeight = (pulse.heavy ? 1.10f : 0.72f) * (0.70f + 0.30f * life);

        for (int i = 0; i < segs; i++) {
            WavePoint a = points.get(i);
            WavePoint b = points.get(i + 1);
            if (a == null || b == null) continue;

            float wobbleA = 0.05f * MathHelper.sin((float) (i * 0.45f) + now * 0.45f + pulse.seed * 0.09f);
            float wobbleB = 0.05f * MathHelper.sin((float) ((i + 1) * 0.45f) + now * 0.45f + pulse.seed * 0.09f);

            float h0 = Math.max(0.18f, baseHeight + wobbleA);
            float h1 = Math.max(0.18f, baseHeight + wobbleB);

            drawWaveSegment(buf, mat, pulse, a, b, h0, h1);
        }
    }

    private static WavePoint traceWaveSurface(World world, Pulse pulse, double angle, float radius) {
        int steps = Math.max(1, MathHelper.ceil(radius / 0.65D));
        double prevY = pulse.baseSurfaceY;
        GroundInfo last = null;

        for (int i = 1; i <= steps; i++) {
            double t = (double) i / (double) steps;
            double r = radius * t;

            double x = pulse.x + Math.cos(angle) * r;
            double z = pulse.z + Math.sin(angle) * r;

            GroundInfo ground = sampleGround(world, x, prevY + 1.2D, z);
            if (ground == null) return null;

            // can climb 1 block, not 2
            if (ground.y - prevY > 1.05D) {
                return null;
            }

            prevY = ground.y;
            last = ground;
        }

        if (last == null) return null;

        float fx = (float) (pulse.x + Math.cos(angle) * radius);
        float fz = (float) (pulse.z + Math.sin(angle) * radius);
        return new WavePoint(fx, (float) last.y, fz, last.rgb);
    }

    private static void drawWaveSegment(BufferBuilder buf, Matrix4f mat, Pulse pulse, WavePoint a, WavePoint b, float h0, float h1) {
        float bandHalf = pulse.heavy ? 0.68f : 0.52f;

        float adx = a.x - (float) pulse.x;
        float adz = a.z - (float) pulse.z;
        float alen = Math.max(0.0001f, MathHelper.sqrt(adx * adx + adz * adz));
        float anx = adx / alen;
        float anz = adz / alen;

        float bdx = b.x - (float) pulse.x;
        float bdz = b.z - (float) pulse.z;
        float blen = Math.max(0.0001f, MathHelper.sqrt(bdx * bdx + bdz * bdz));
        float bnx = bdx / blen;
        float bnz = bdz / blen;

        float aInX = a.x - anx * bandHalf;
        float aInZ = a.z - anz * bandHalf;
        float aOutX = a.x + anx * bandHalf;
        float aOutZ = a.z + anz * bandHalf;

        float bInX = b.x - bnx * bandHalf;
        float bInZ = b.z - bnz * bandHalf;
        float bOutX = b.x + bnx * bandHalf;
        float bOutZ = b.z + bnz * bandHalf;

        int avg = avgColor(a.rgb, b.rgb);

        int bodyR = mix((avg >> 16) & 255, pulse.heavy ? 170 : 135, pulse.heavy ? 0.22f : 0.14f);
        int bodyG = mix((avg >> 8) & 255, 28, pulse.heavy ? 0.15f : 0.10f);
        int bodyB = mix(avg & 255, 24, pulse.heavy ? 0.15f : 0.10f);

        int glowR = 255;
        int glowG = pulse.heavy ? 56 : 42;
        int glowB = pulse.heavy ? 48 : 38;

        float topY0 = a.y + h0;
        float topY1 = b.y + h1;

        // main lifted earth strip
        quad(
                buf, mat,
                aInX, topY0, aInZ,
                aOutX, topY0, aOutZ,
                bOutX, topY1, bOutZ,
                bInX, topY1, bInZ,
                bodyR, bodyG, bodyB, pulse.heavy ? 220 : 205
        );

        // outer face
        quad(
                buf, mat,
                aOutX, a.y, aOutZ,
                aOutX, topY0, aOutZ,
                bOutX, topY1, bOutZ,
                bOutX, b.y, bOutZ,
                darker(bodyR), darker(bodyG), darker(bodyB), 175
        );

        // inner face
        quad(
                buf, mat,
                aInX, a.y, aInZ,
                bInX, b.y, bInZ,
                bInX, topY1, bInZ,
                aInX, topY0, aInZ,
                darker2(bodyR), darker2(bodyG), darker2(bodyB), 165
        );

        // glowing core strip on top
        float glowInset = pulse.heavy ? 0.16f : 0.13f;
        float aGlowInX = aInX + anx * glowInset;
        float aGlowInZ = aInZ + anz * glowInset;
        float aGlowOutX = aOutX - anx * glowInset;
        float aGlowOutZ = aOutZ - anz * glowInset;

        float bGlowInX = bInX + bnx * glowInset;
        float bGlowInZ = bInZ + bnz * glowInset;
        float bGlowOutX = bOutX - bnx * glowInset;
        float bGlowOutZ = bOutZ - bnz * glowInset;

        quad(
                buf, mat,
                aGlowInX, topY0 + 0.02f, aGlowInZ,
                aGlowOutX, topY0 + 0.02f, aGlowOutZ,
                bGlowOutX, topY1 + 0.02f, bGlowOutZ,
                bGlowInX, topY1 + 0.02f, bGlowInZ,
                glowR, glowG, glowB, pulse.heavy ? 150 : 118
        );

        // low ground glow skirt
        quad(
                buf, mat,
                aInX, a.y + 0.02f, aInZ,
                aOutX, a.y + 0.02f, aOutZ,
                bOutX, b.y + 0.02f, bOutZ,
                bInX, b.y + 0.02f, bInZ,
                glowR, glowG, glowB, pulse.heavy ? 64 : 44
        );
    }

    private static GroundInfo sampleGround(World world, double x, double aroundY, double z) {
        int ix = MathHelper.floor(x);
        int iz = MathHelper.floor(z);
        int top = MathHelper.floor(aroundY + 3.0D);
        int bottom = MathHelper.floor(aroundY - 8.0D);

        for (int y = top; y >= bottom; y--) {
            BlockPos pos = new BlockPos(ix, y, iz);
            BlockState state = world.getBlockState(pos);
            if (!state.isSolidBlock(world, pos)) continue;

            BlockPos above = pos.up();
            if (world.getBlockState(above).isSolidBlock(world, above)) continue;

            int rgb = state.getMapColor(world, pos).color;
            if (rgb == 0) rgb = 0x6C6158;

            return new GroundInfo(y + 1.0D, rgb);
        }

        return null;
    }

    private static void quad(BufferBuilder buf, Matrix4f mat,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float x4, float y4, float z4,
                             int r, int g, int b, int a) {
        v(buf, mat, x1, y1, z1, r, g, b, a);
        v(buf, mat, x2, y2, z2, r, g, b, a);
        v(buf, mat, x3, y3, z3, r, g, b, a);

        v(buf, mat, x1, y1, z1, r, g, b, a);
        v(buf, mat, x3, y3, z3, r, g, b, a);
        v(buf, mat, x4, y4, z4, r, g, b, a);
    }

    private static void v(BufferBuilder buf, Matrix4f mat, float x, float y, float z, int r, int g, int b, int a) {
        buf.vertex(mat, x, y, z).color(r, g, b, a).next();
    }

    private static int darker(int c) {
        return Math.max(0, (int) (c * 0.78f));
    }

    private static int darker2(int c) {
        return Math.max(0, (int) (c * 0.64f));
    }

    private static int mix(int a, int b, float t) {
        return MathHelper.clamp((int) (a + (b - a) * t), 0, 255);
    }

    private static int avgColor(int a, int b) {
        int ar = (a >> 16) & 255;
        int ag = (a >> 8) & 255;
        int ab = a & 255;

        int br = (b >> 16) & 255;
        int bg = (b >> 8) & 255;
        int bb = b & 255;

        return ((ar + br) / 2 << 16) | ((ag + bg) / 2 << 8) | ((ab + bb) / 2);
    }
}
