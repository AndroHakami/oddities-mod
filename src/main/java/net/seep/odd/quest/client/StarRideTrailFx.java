package net.seep.odd.quest.client;

import com.mojang.blaze3d.systems.RenderSystem;
import ladysnake.satin.api.event.PostWorldRenderCallback;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.entity.star_ride.StarRideEntity;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Environment(EnvType.CLIENT)
public final class StarRideTrailFx implements PostWorldRenderCallback {
    private static final double MAX_TRAIL_LENGTH = 12.6D; // 3x longer than 4.2
    private static final int MAX_POINTS = 66;
    private static boolean inited;

    private static final class TrailPoint {
        final Vec3d pos;
        final Vec3d dir;
        final float width;
        final long age;

        TrailPoint(Vec3d pos, Vec3d dir, float width, long age) {
            this.pos = pos;
            this.dir = dir;
            this.width = width;
            this.age = age;
        }
    }

    private static final Map<Integer, ArrayDeque<TrailPoint>> TRAILS = new HashMap<>();

    private StarRideTrailFx() {
    }

    public static void init() {
        if (inited) return;
        inited = true;
        PostWorldRenderCallback.EVENT.register(new StarRideTrailFx());
    }

    @Override
    public void onWorldRendered(Camera camera, float tickDelta, long limitTime) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;

        long now = client.world.getTime();
        List<StarRideEntity> rides = client.world.getEntitiesByClass(
                StarRideEntity.class,
                client.player.getBoundingBox().expand(180.0D),
                entity -> !entity.isRemoved()
        );

        Map<Integer, Boolean> seen = new HashMap<>();
        for (StarRideEntity ride : rides) {
            seen.put(ride.getId(), Boolean.TRUE);
            pushTrailPoint(client, ride, tickDelta, now);
        }

        TRAILS.entrySet().removeIf(entry -> !seen.containsKey(entry.getKey()));

        Vec3d cam = camera.getPos();
        MatrixStack matrices = new MatrixStack();
        matrices.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f posMat = matrices.peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);

        VertexConsumerProvider.Immediate immediate = client.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer vc = immediate.getBuffer(RenderLayer.getLightning());

        for (ArrayDeque<TrailPoint> trail : TRAILS.values()) {
            renderTrail(vc, posMat, trail, now, tickDelta);
        }

        immediate.draw();

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void pushTrailPoint(MinecraftClient client, StarRideEntity ride, float tickDelta, long now) {
        Vec3d pos = ride.getLerpedPos(tickDelta);
        float yaw = ride.getYaw(tickDelta) * 0.017453292F;
        Vec3d backDir = new Vec3d(MathHelper.sin(yaw), 0.0D, -MathHelper.cos(yaw)).normalize();
        Vec3d start = pos.subtract(backDir.multiply(1.05D)).add(0.0D, 0.30D, 0.0D);

        double speed = Math.sqrt(ride.getVelocity().horizontalLengthSquared());
        float width = MathHelper.clamp((float) (0.84D + speed * 0.12D), 0.84F, 1.10F);

        ArrayDeque<TrailPoint> trail = TRAILS.computeIfAbsent(ride.getId(), id -> new ArrayDeque<>());
        TrailPoint last = trail.peekLast();

        double minSpacing = speed > 0.20D ? 0.18D : 0.10D;
        if (last == null || last.pos.squaredDistanceTo(start) > minSpacing * minSpacing || now != last.age) {
            trail.addLast(new TrailPoint(start, backDir, width, now));
        }

        while (trail.size() > MAX_POINTS || cumulativeLength(trail) > MAX_TRAIL_LENGTH) {
            trail.pollFirst();
        }

        if (((now + ride.getId()) % 5L) == 0L) {
            client.world.addParticle(
                    ParticleTypes.POOF,
                    start.x + (client.world.random.nextDouble() - 0.5D) * 0.03D,
                    start.y - 0.01D,
                    start.z + (client.world.random.nextDouble() - 0.5D) * 0.03D,
                    -backDir.x * 0.02D,
                    0.004D,
                    -backDir.z * 0.02D
            );
        }
    }

    private static double cumulativeLength(ArrayDeque<TrailPoint> trail) {
        double len = 0.0D;
        TrailPoint prev = null;
        for (TrailPoint point : trail) {
            if (prev != null) {
                len += prev.pos.distanceTo(point.pos);
            }
            prev = point;
        }
        return len;
    }

    private static float fract(float value) {
        return value - (float) Math.floor(value);
    }

    private static void renderTrail(VertexConsumer vc, Matrix4f posMat, ArrayDeque<TrailPoint> trail, long now, float tickDelta) {
        if (trail.size() < 2) return;

        TrailPoint[] points = trail.toArray(new TrailPoint[0]);
        double total = 0.0D;
        for (int i = 1; i < points.length; i++) {
            total += points[i - 1].pos.distanceTo(points[i].pos);
        }
        if (total < 0.05D) return;

        double walked = 0.0D;
        for (int i = points.length - 1; i > 0; i--) {
            TrailPoint newer = points[i];
            TrailPoint older = points[i - 1];
            double segLen = older.pos.distanceTo(newer.pos);
            if (segLen < 0.0001D) continue;

            double t0 = walked / total;
            double t1 = (walked + segLen) / total;
            walked += segLen;

            Vec3d segDir = newer.pos.subtract(older.pos).normalize();
            Vec3d side = new Vec3d(-segDir.z, 0.0D, segDir.x);
            if (side.lengthSquared() < 0.0001D) {
                side = new Vec3d(-older.dir.z, 0.0D, older.dir.x);
            }
            side = side.normalize();

            float width0 = older.width * (float) MathHelper.lerp(1.0D - t0, 0.28D, 1.0D);
            float width1 = newer.width * (float) MathHelper.lerp(1.0D - t1, 0.28D, 1.0D);

            Vec3d l0 = older.pos.add(side.multiply(width0 * 0.5F));
            Vec3d r0 = older.pos.subtract(side.multiply(width0 * 0.5F));
            Vec3d l1 = newer.pos.add(side.multiply(width1 * 0.5F));
            Vec3d r1 = newer.pos.subtract(side.multiply(width1 * 0.5F));

            float alpha0 = (float) MathHelper.clamp((1.0D - t0) * 0.72D, 0.0D, 0.72D);
            float alpha1 = (float) MathHelper.clamp((1.0D - t1) * 0.72D, 0.0D, 0.72D);

            float hue0 = fract((now + tickDelta) * 0.035F - (float) t0 * 0.55F);
            float hue1 = fract((now + tickDelta) * 0.035F - (float) t1 * 0.55F);
            int rgb0 = MathHelper.hsvToRgb(hue0, 0.80F, 1.0F);
            int rgb1 = MathHelper.hsvToRgb(hue1, 0.80F, 1.0F);

            emitQuad(vc, posMat, l0, l1, r1, r0, rgb0, alpha0, rgb1, alpha1);
        }
    }

    private static void emitQuad(VertexConsumer vc, Matrix4f mat,
                                 Vec3d a, Vec3d b, Vec3d c, Vec3d d,
                                 int rgb0, float a0,
                                 int rgb1, float a1) {
        float r0 = ((rgb0 >> 16) & 255) / 255.0F;
        float g0 = ((rgb0 >> 8) & 255) / 255.0F;
        float b0 = (rgb0 & 255) / 255.0F;
        float r1 = ((rgb1 >> 16) & 255) / 255.0F;
        float g1 = ((rgb1 >> 8) & 255) / 255.0F;
        float b1 = (rgb1 & 255) / 255.0F;

        vc.vertex(mat, (float) a.x, (float) a.y, (float) a.z).color(r0, g0, b0, a0).next();
        vc.vertex(mat, (float) b.x, (float) b.y, (float) b.z).color(r1, g1, b1, a1).next();
        vc.vertex(mat, (float) c.x, (float) c.y, (float) c.z).color(r1, g1, b1, a1).next();
        vc.vertex(mat, (float) d.x, (float) d.y, (float) d.z).color(r0, g0, b0, a0).next();
    }
}
