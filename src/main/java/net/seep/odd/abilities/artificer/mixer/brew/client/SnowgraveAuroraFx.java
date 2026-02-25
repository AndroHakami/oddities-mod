// FILE: src/main/java/net/seep/odd/abilities/artificer/mixer/brew/client/SnowgraveAuroraFx.java
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

import net.seep.odd.abilities.artificer.mixer.SnowgraveNet;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public final class SnowgraveAuroraFx implements PostWorldRenderCallback {
    private SnowgraveAuroraFx() {}

    private static final Identifier POST_ID = new Identifier("odd", "shaders/post/snowgrave_aurora.json");
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

    private record Zone(double x, double y, double z, float radius, float height, long startWorldTick, int durationTicks) {}
    private static final Long2ObjectOpenHashMap<Zone> ZONES = new Long2ObjectOpenHashMap<>();

    public static void init() {
        if (inited) return;
        inited = true;

        PostWorldRenderCallback.EVENT.register(new SnowgraveAuroraFx());

        ClientPlayNetworking.registerGlobalReceiver(SnowgraveNet.SNOWGRAVE_ZONE_S2C, (mc, handler, buf, rs) -> {
            long id = buf.readLong();
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            float r = buf.readFloat();
            int dur = buf.readInt();

            mc.execute(() -> spawn(id, x, y, z, r, 7.0f, dur));
        });

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc == null || mc.world == null) return;
            long now = mc.world.getTime();
            var it = ZONES.long2ObjectEntrySet().fastIterator();
            while (it.hasNext()) {
                var e = it.next();
                Zone z = e.getValue();
                if ((now - z.startWorldTick) >= z.durationTicks) it.remove();
            }
        });
    }

    private static void spawn(long id, double x, double y, double z, float radius, float height, int durationTicks) {
        if (client == null || client.world == null) return;
        ZONES.put(id, new Zone(x, y, z, radius, height, client.world.getTime(), durationTicks));
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
        if (client == null || client.world == null || ZONES.isEmpty()) return;

        ensureInit();
        if (shader == null) return;

        ClientWorld world = client.world;
        long now = world.getTime();

        var camPos = camera.getPos();
        uInvTransform.set(GlMatrices.getInverseTransformMatrix(tmpMat));
        uCameraPos.set((float) camPos.x, (float) camPos.y, (float) camPos.z);

        float time = ((client.player != null ? client.player.age : 0) + tickDelta) / 20.0f;
        uTime.set(time);

        var it = ZONES.long2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            var e = it.next();
            Zone z = e.getValue();

            float age01 = (float)((now - z.startWorldTick) + tickDelta) / (float)z.durationTicks;
            if (age01 >= 1.0f) { it.remove(); continue; }
            age01 = MathHelper.clamp(age01, 0f, 1f);

            // charge feel: strong ramp up, then hold
            float fadeIn = smoothstep(0.00f, 0.35f, age01);
            float intensity = fadeIn;

            uCenter.set((float)z.x, (float)z.y, (float)z.z);
            uRadius.set(z.radius);
            uHeight.set(z.height);

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
