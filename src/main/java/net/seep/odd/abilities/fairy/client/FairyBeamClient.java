package net.seep.odd.abilities.fairy.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BeaconBlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import net.seep.odd.abilities.power.FairyPower;
import net.seep.odd.status.ModStatusEffects;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Environment(EnvType.CLIENT)
public final class FairyBeamClient {
    private FairyBeamClient() {}

    private static boolean inited = false;
    private static final Set<UUID> BEAM_ON = ConcurrentHashMap.newKeySet();

    public static void init() {
        if (inited) return;
        inited = true;

        ClientPlayNetworking.registerGlobalReceiver(FairyPower.S2C_BEAM_STATE, (client, handler, buf, resp) -> {
            UUID who = buf.readUuid();
            boolean on = buf.readBoolean();
            client.execute(() -> {
                if (on) BEAM_ON.add(who);
                else BEAM_ON.remove(who);
            });
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> BEAM_ON.clear());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> BEAM_ON.clear());

        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            ClientWorld world = context.world();
            if (world == null) return;

            VertexConsumerProvider consumers = context.consumers();
            if (consumers == null) return;

            MatrixStack matrices = context.matrixStack();
            Vec3d camPos = context.camera().getPos();

            float tickDelta = context.tickDelta();
            long worldTime = world.getTime();

            for (PlayerEntity pe : world.getPlayers()) {
                if (!BEAM_ON.contains(pe.getUuid())) continue;
                if (pe.hasStatusEffect(ModStatusEffects.POWERLESS)) continue;

                // ✅ torso start (scales with Pehkui + pose)
                float h = pe.getDimensions(pe.getPose()).height;

                double px = MathHelper.lerp(tickDelta, pe.prevX, pe.getX());
                double py = MathHelper.lerp(tickDelta, pe.prevY, pe.getY());
                double pz = MathHelper.lerp(tickDelta, pe.prevZ, pe.getZ());

                Vec3d dir = pe.getRotationVec(tickDelta).normalize();

                double torsoY = py + h * 0.58;
                double forward = h * 0.16;
                Vec3d start = new Vec3d(px, torsoY, pz).add(dir.multiply(forward));

                Vec3d end = start.add(dir.multiply(FairyPower.BEAM_RANGE));
                HitResult hr = world.raycast(new RaycastContext(
                        start, end,
                        RaycastContext.ShapeType.OUTLINE,
                        RaycastContext.FluidHandling.NONE,
                        pe
                ));

                Vec3d hitPos = (hr != null) ? hr.getPos() : end;
                double dist = Math.max(0.5, hitPos.distanceTo(start));
                int beamLen = Math.max(1, (int) Math.ceil(dist));

                float[] rgb = iridescentRgb(worldTime, tickDelta);

                // ✅ 40% smaller + scales with fairy size
                float sizeScale = h / 1.8f;
                float inner = 0.12f * 0.60f * sizeScale;
                float outer = 0.18f * 0.60f * sizeScale;

                matrices.push();

                // ✅ CAMERA TRANSLATION (this is the missing piece that made it disappear)
                matrices.translate(start.x - camPos.x, start.y - camPos.y, start.z - camPos.z);

                // rotate +Y into look direction (stable even straight up/down)
                Quaternionf q = safeRotateTo(
                        new Vector3f(0f, 1f, 0f),
                        new Vector3f((float) dir.x, (float) dir.y, (float) dir.z)
                );
                matrices.multiply(q);

                // ✅ CRITICAL: cancel beacon beam's built-in local (0.5, 0, 0.5) center offset
                // this keeps the beam truly coming from your torso and NOT orbiting when you look around.
                matrices.translate(-0.5, 0.0, -0.5);

                BeaconBlockEntityRenderer.renderBeam(
                        matrices, consumers,
                        BeaconBlockEntityRenderer.BEAM_TEXTURE,
                        tickDelta, 1.0f,
                        worldTime,
                        0, beamLen,
                        rgb,
                        inner, outer
                );

                matrices.pop();
            }
        });
    }

    private static Quaternionf safeRotateTo(Vector3f from, Vector3f to) {
        to.normalize();
        float dot = from.dot(to);
        if (dot > 0.9999f) return new Quaternionf();
        if (dot < -0.9999f) return new Quaternionf().rotateX((float) Math.PI);
        return new Quaternionf().rotateTo(from, to);
    }

    private static float[] iridescentRgb(long worldTime, float tickDelta) {
        float t = (worldTime + tickDelta) / 240.0f;
        float hue = (t % 1.0f + 1.0f) % 1.0f;
        int rgb = hsvToRgb(hue, 0.80f, 1.0f);
        return new float[]{
                ((rgb >> 16) & 255) / 255f,
                ((rgb >> 8) & 255) / 255f,
                (rgb & 255) / 255f
        };
    }

    private static int hsvToRgb(float h, float s, float v) {
        h = (h % 1f + 1f) % 1f;
        s = MathHelper.clamp(s, 0f, 1f);
        v = MathHelper.clamp(v, 0f, 1f);

        float c = v * s;
        float x = c * (1f - Math.abs((h * 6f) % 2f - 1f));
        float m = v - c;

        float r1, g1, b1;
        float hh = h * 6f;
        if (hh < 1f)      { r1 = c; g1 = x; b1 = 0; }
        else if (hh < 2f) { r1 = x; g1 = c; b1 = 0; }
        else if (hh < 3f) { r1 = 0; g1 = c; b1 = x; }
        else if (hh < 4f) { r1 = 0; g1 = x; b1 = c; }
        else if (hh < 5f) { r1 = x; g1 = 0; b1 = c; }
        else              { r1 = c; g1 = 0; b1 = x; }

        int r = MathHelper.clamp((int)((r1 + m) * 255f), 0, 255);
        int g = MathHelper.clamp((int)((g1 + m) * 255f), 0, 255);
        int b = MathHelper.clamp((int)((b1 + m) * 255f), 0, 255);
        return (r << 16) | (g << 8) | b;
    }
}