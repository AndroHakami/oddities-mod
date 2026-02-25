// FILE: src/main/java/net/seep/odd/abilities/artificer/mixer/brew/client/BlackFlameFx.java
package net.seep.odd.abilities.artificer.mixer.brew.client;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import ladysnake.satin.api.event.PostWorldRenderCallback;
import ladysnake.satin.api.experimental.ReadableDepthFramebuffer;
import ladysnake.satin.api.managed.ManagedShaderEffect;
import ladysnake.satin.api.managed.ShaderEffectManager;
import ladysnake.satin.api.managed.uniform.Uniform1f;
import ladysnake.satin.api.managed.uniform.Uniform3f;
import ladysnake.satin.api.managed.uniform.UniformMat4;
import ladysnake.satin.api.util.GlMatrices;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import net.seep.odd.abilities.artificer.mixer.brew.BlackFlameNet;

import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public final class BlackFlameFx implements PostWorldRenderCallback {
    private BlackFlameFx() {}

    private static final Identifier POST_ID = new Identifier("odd", "shaders/post/black_flame.json");
    private static boolean inited = false;

    private static final MinecraftClient client = MinecraftClient.getInstance();
    private static final Matrix4f tmpMat = new Matrix4f();

    private static ManagedShaderEffect shader;

    private static UniformMat4 uInvTransform;
    private static Uniform3f  uCameraPos;

    private static Uniform3f  uCenter;
    private static Uniform1f  uRadius;
    private static Uniform1f  uHeight;

    private static Uniform1f  uTime;
    private static Uniform1f  uIntensity;
    private static Uniform1f  uAge01;

    private record Flame(double x, double y, double z,
                         float radius, float height,
                         long endWorldTick, int totalDurationTicks) {}

    private static final Long2ObjectOpenHashMap<Flame> FLAMES = new Long2ObjectOpenHashMap<>();

    public static void init() {
        if (inited) return;
        inited = true;

        PostWorldRenderCallback.EVENT.register(new BlackFlameFx());

        // ✅ receiver (supports resync)
        ClientPlayNetworking.registerGlobalReceiver(BlackFlameNet.BLACK_FLAME_S2C, (mc, handler, buf, responseSender) -> {
            long id = buf.readLong();
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            float r = buf.readFloat();
            float h = buf.readFloat();
            int remaining = buf.readInt();
            int total = buf.readInt();

            mc.execute(() -> upsert(id, x, y, z, r, h, remaining, total));
        });

        // ✅ cleanup on real ticks
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc == null || mc.world == null) return;
            long now = mc.world.getTime();

            var it = FLAMES.long2ObjectEntrySet().fastIterator();
            while (it.hasNext()) {
                var e = it.next();
                Flame f = e.getValue();
                if (now >= f.endWorldTick) it.remove();
            }
        });
    }

    private static void upsert(long id, double x, double y, double z, float radius, float height,
                               int remainingTicks, int totalDurationTicks) {
        if (client == null || client.world == null) return;

        int total = Math.max(1, totalDurationTicks);
        int rem   = MathHelper.clamp(remainingTicks, 0, total);

        long end = client.world.getTime() + rem;

        // refresh / insert without resetting "start time" incorrectly
        FLAMES.put(id, new Flame(x, y, z, radius, height, end, total));
    }

    private static void ensureInit() {
        if (shader != null) return;

        shader = ShaderEffectManager.getInstance().manage(POST_ID, s -> {
            if (client != null && client.getFramebuffer() instanceof ReadableDepthFramebuffer rdf) {
                s.setSamplerUniform("DepthSampler", rdf.getStillDepthMap());
            }
        });

        uInvTransform = shader.findUniformMat4("InverseTransformMatrix");
        uCameraPos    = shader.findUniform3f("CameraPosition");

        uCenter       = shader.findUniform3f("Center");
        uRadius       = shader.findUniform1f("Radius");
        uHeight       = shader.findUniform1f("Height");

        uTime         = shader.findUniform1f("iTime");
        uIntensity    = shader.findUniform1f("Intensity");
        uAge01        = shader.findUniform1f("Age01");
    }

    @Override
    public void onWorldRendered(Camera camera, float tickDelta, long limitTime) {
        if (client == null || client.world == null || FLAMES.isEmpty()) return;

        ensureInit();
        if (shader == null) return;

        ClientWorld world = client.world;
        long now = world.getTime();

        var camPos = camera.getPos();
        uInvTransform.set(GlMatrices.getInverseTransformMatrix(tmpMat));
        uCameraPos.set((float) camPos.x, (float) camPos.y, (float) camPos.z);

        float time = ((client.player != null ? client.player.age : 0) + tickDelta) / 20.0f;
        uTime.set(time);

        // render each flame (safe with resync)
        var it = FLAMES.long2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            var e = it.next();
            Flame f = e.getValue();

            float remaining = (float)((f.endWorldTick - now) - tickDelta);
            if (remaining <= 0.0f) { it.remove(); continue; }

            float rem01 = MathHelper.clamp(remaining / (float)f.totalDurationTicks, 0f, 1f);
            float age01 = 1.0f - rem01;

            // fast emerge, steady burn, softer fade
            float fadeIn  = smoothstep(0.00f, 0.06f, age01);
            float fadeOut = smoothstep(0.00f, 0.30f, rem01);

            float pop = 1.0f + 0.55f * (1.0f - smoothstep(0.00f, 0.12f, age01));
            float intensity = fadeIn * fadeOut * pop;

            uCenter.set((float) f.x, (float) f.y, (float) f.z);
            uRadius.set(f.radius);
            uHeight.set(f.height);

            uAge01.set(age01);
            uIntensity.set(intensity);

            shader.render(tickDelta);
        }
    }

    private static float smoothstep(float a, float b, float x) {
        float t = MathHelper.clamp((x - a) / (b - a), 0f, 1f);
        return t * t * (3f - 2f * t);
    }
}
