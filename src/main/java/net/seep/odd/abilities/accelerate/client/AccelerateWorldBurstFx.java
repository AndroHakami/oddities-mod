package net.seep.odd.abilities.accelerate.client;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public final class AccelerateWorldBurstFx {
    private AccelerateWorldBurstFx() {}

    private static final Identifier S2C = new Identifier("odd", "accelerate_recall_world_burst");

    private static final class Burst {
        final Vec3d pos;
        final long startTick;
        final int seed;
        Burst(Vec3d pos, long startTick, int seed) {
            this.pos = pos;
            this.startTick = startTick;
            this.seed = seed;
        }
    }

    private static final ObjectArrayList<Burst> BURSTS = new ObjectArrayList<>();
    private static boolean inited = false;

    private static final int LIFE_TICKS = 10;

    public static void init() {
        if (inited) return;
        inited = true;

        ClientPlayNetworking.registerGlobalReceiver(S2C, (client, handler, buf, responder) -> {
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            int seed = buf.readInt();

            client.execute(() -> {
                if (client.world == null) return;
                BURSTS.add(new Burst(new Vec3d(x, y, z), client.world.getTime(), seed));
            });
        });

        WorldRenderEvents.AFTER_ENTITIES.register(ctx -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.world == null) return;
            if (BURSTS.isEmpty()) return;

            Camera cam = ctx.camera();
            Vec3d camPos = cam.getPos();

            MatrixStack matrices = ctx.matrixStack();
            VertexConsumerProvider consumers = ctx.consumers();
            if (matrices == null || consumers == null) return;

            long now = mc.world.getTime();
            float td = ctx.tickDelta();

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.lineWidth(2.0f);

            VertexConsumer vc = consumers.getBuffer(RenderLayer.getLines());

            for (int i = BURSTS.size() - 1; i >= 0; i--) {
                Burst b = BURSTS.get(i);
                float age = (now - b.startTick) + td;
                if (age >= LIFE_TICKS) {
                    BURSTS.remove(i);
                    continue;
                }

                float t = age / (float) LIFE_TICKS;   // 0..1
                float inv = 1f - t;
                float intensity = inv * inv;

                double radius = 0.15 + 2.10 * t;

                // red lightning bolts
                int bolts = 10;
                for (int k = 0; k < bolts; k++) {
                    int seed = b.seed ^ (k * 0x9E3779B9);
                    Vec3d dir = randomDir(seed);
                    dir = new Vec3d(dir.x, MathHelper.clamp(dir.y + 0.25, -0.2, 1.0), dir.z).normalize();

                    Vec3d start = b.pos.add(0.0, 1.0, 0.0).add(dir.multiply(0.15));
                    Vec3d end = b.pos.add(0.0, 1.0, 0.0).add(dir.multiply(radius));

                    drawCrackBolt(matrices, vc, camPos, start, end, seed, intensity);
                }

                // golden core starburst
                int rays = 18;
                for (int rI = 0; rI < rays; rI++) {
                    int seed = b.seed + 1337 + rI * 41;
                    Vec3d dir = randomDir(seed);
                    dir = new Vec3d(dir.x, dir.y * 0.35, dir.z).normalize();

                    Vec3d start = b.pos.add(0.0, 1.0, 0.0);
                    Vec3d end = start.add(dir.multiply(0.65 + 0.70 * t));

                    drawRay(matrices, vc, camPos, start, end, intensity);
                }
            }

            RenderSystem.lineWidth(1.0f);
            RenderSystem.disableBlend();
        });
    }

    private static void drawRay(MatrixStack matrices, VertexConsumer vc, Vec3d camPos, Vec3d a, Vec3d b, float intensity) {
        float rr = 1.00f, gg = 0.92f, bb = 0.20f;
        float aa = 0.55f * intensity;
        addLine(vc, matrices.peek(), camPos, a, b, rr, gg, bb, aa);
    }

    private static void drawCrackBolt(MatrixStack matrices, VertexConsumer vc, Vec3d camPos,
                                      Vec3d start, Vec3d end, int seed, float intensity) {
        int segs = 9;
        Vec3d prev = start;

        for (int i = 1; i <= segs; i++) {
            float t = i / (float) segs;
            Vec3d base = start.lerp(end, t);

            double j = (0.10 + 0.22 * t) * intensity;
            double ox = (randSigned(seed + i * 17) * j);
            double oy = (randSigned(seed + i * 29) * j);
            double oz = (randSigned(seed + i * 43) * j);

            Vec3d p = base.add(ox, oy, oz);

            // gold -> red
            float rr = 1.00f;
            float gg = MathHelper.lerp(t, 0.95f, 0.18f);
            float bb = MathHelper.lerp(t, 0.20f, 0.10f);
            float aa = (0.35f + 0.55f * (1.0f - t)) * intensity;

            addLine(vc, matrices.peek(), camPos, prev, p, rr, gg, bb, aa);
            prev = p;
        }
    }

    private static void addLine(VertexConsumer vc, MatrixStack.Entry e, Vec3d camPos,
                                Vec3d a, Vec3d b, float r, float g, float bl, float aA) {
        float ax = (float)(a.x - camPos.x);
        float ay = (float)(a.y - camPos.y);
        float az = (float)(a.z - camPos.z);

        float bx = (float)(b.x - camPos.x);
        float by = (float)(b.y - camPos.y);
        float bz = (float)(b.z - camPos.z);

        vc.vertex(e.getPositionMatrix(), ax, ay, az)
                .color(r, g, bl, MathHelper.clamp(aA, 0f, 1f))
                .normal(e.getNormalMatrix(), 0f, 1f, 0f)
                .next();

        vc.vertex(e.getPositionMatrix(), bx, by, bz)
                .color(r, g, bl, MathHelper.clamp(aA, 0f, 1f))
                .normal(e.getNormalMatrix(), 0f, 1f, 0f)
                .next();
    }

    private static Vec3d randomDir(int seed) {
        float u = rand01(seed);
        float v = rand01(seed + 1);
        double theta = u * Math.PI * 2.0;
        double z = (v * 2.0) - 1.0;
        double r = Math.sqrt(Math.max(0.0, 1.0 - z * z));
        return new Vec3d(r * Math.cos(theta), z, r * Math.sin(theta));
    }

    private static float rand01(int seed) {
        int x = mix(seed);
        return (x & 0xFFFF) / 65535.0f;
    }

    private static float randSigned(int seed) {
        return (rand01(seed) * 2.0f) - 1.0f;
    }

    private static int mix(int x) {
        x ^= (x << 13);
        x ^= (x >>> 17);
        x ^= (x << 5);
        return x;
    }
}
