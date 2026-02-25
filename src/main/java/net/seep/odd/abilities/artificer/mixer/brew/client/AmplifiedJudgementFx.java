// FILE: src/main/java/net/seep/odd/abilities/artificer/mixer/brew/client/AmplifiedJudgementFx.java
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

import net.seep.odd.abilities.artificer.mixer.AmplifiedJudgementNet;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public final class AmplifiedJudgementFx implements PostWorldRenderCallback {
    private AmplifiedJudgementFx() {}

    private static final Identifier POST_ID = new Identifier("odd", "shaders/post/amplified_judgement.json");
    private static boolean inited = false;

    private static final MinecraftClient client = MinecraftClient.getInstance();
    private static final Matrix4f tmpMat = new Matrix4f();

    private static ManagedShaderEffect shader;

    private static UniformMat4 uInvTransform;
    private static Uniform3f  uCameraPos;

    private static Uniform3f  uCenter;
    private static Uniform1f  uRadius;
    private static Uniform1f  uHeight;

    private static Uniform1f  uMode;
    private static Uniform1f  uTime;
    private static Uniform1f  uIntensity;
    private static Uniform1f  uAge01;

    private record Cast(double x, double y, double z, int mode, float radius, float height, long startWorldTick, int durationTicks) {}
    private static final Long2ObjectOpenHashMap<Cast> CASTS = new Long2ObjectOpenHashMap<>();

    public static void init() {
        if (inited) return;
        inited = true;

        PostWorldRenderCallback.EVENT.register(new AmplifiedJudgementFx());

        ClientPlayNetworking.registerGlobalReceiver(AmplifiedJudgementNet.S2C, (mc, handler, buf, rs) -> {
            long id = buf.readLong();
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            int mode = buf.readInt();
            int dur = buf.readInt();

            mc.execute(() -> spawn(id, x, y, z, mode, dur));
        });

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc == null || mc.world == null) return;
            long now = mc.world.getTime();

            var it = CASTS.long2ObjectEntrySet().fastIterator();
            while (it.hasNext()) {
                var e = it.next();
                Cast c = e.getValue();
                if ((now - c.startWorldTick) >= c.durationTicks) it.remove();
            }
        });
    }

    private static void spawn(long id, double x, double y, double z, int mode, int durationTicks) {
        if (client == null || client.world == null) return;

        // tune sizes per mode
        // Mode 0 (charge) MUST stay unchanged
        float radius = (mode == 0) ? 9.9f : 10.0f;   // ✅ beam is now 10-block radius
        float height = (mode == 0) ? 14.0f : 350.0f;

        CASTS.put(id, new Cast(x, y, z, mode, radius, height, client.world.getTime(), durationTicks));
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

        uMode         = shader.findUniform1f("Mode");
        uTime         = shader.findUniform1f("iTime");
        uIntensity    = shader.findUniform1f("Intensity");
        uAge01        = shader.findUniform1f("Age01");
    }

    @Override
    public void onWorldRendered(Camera camera, float tickDelta, long limitTime) {
        if (client == null || client.world == null || CASTS.isEmpty()) return;

        ensureInit();
        if (shader == null) return;

        ClientWorld world = client.world;
        long now = world.getTime();

        var camPos = camera.getPos();
        uInvTransform.set(GlMatrices.getInverseTransformMatrix(tmpMat));
        uCameraPos.set((float) camPos.x, (float) camPos.y, (float) camPos.z);

        float time = ((client.player != null ? client.player.age : 0) + tickDelta) / 20.0f;
        uTime.set(time);

        var it = CASTS.long2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            var e = it.next();
            Cast c = e.getValue();

            float age01 = (float)((now - c.startWorldTick) + tickDelta) / (float)c.durationTicks;
            if (age01 >= 1.0f) { it.remove(); continue; }
            age01 = MathHelper.clamp(age01, 0f, 1f);

            float intensity;
            if (c.mode == 0) {
                // charge: ramp up, no harsh fade
                intensity = smoothstep(0.00f, 0.85f, age01);
            } else {
                // beam: fast in + gentle out
                float in  = smoothstep(0.00f, 0.10f, age01);
                float out = smoothstep(0.00f, 0.25f, 1.0f - age01);
                intensity = in * out;
            }

            uCenter.set((float)c.x, (float)c.y, (float)c.z);
            uRadius.set(c.radius);
            uHeight.set(c.height);

            uMode.set((float)c.mode);
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