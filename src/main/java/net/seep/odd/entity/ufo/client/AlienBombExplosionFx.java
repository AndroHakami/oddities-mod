package net.seep.odd.entity.ufo.client;

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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public final class AlienBombExplosionFx implements PostWorldRenderCallback {
    private static final Identifier POST_ID = new Identifier("odd", "shaders/post/alien_bomb_explosion.json");

    private static final MinecraftClient client = MinecraftClient.getInstance();
    private static final Matrix4f tmpMat = new Matrix4f();
    private static boolean inited = false;

    private static ManagedShaderEffect shader;
    private static UniformMat4 uInvTransform;
    private static Uniform3f uCameraPos;
    private static Uniform3f uCenter;
    private static Uniform1f uRadius;
    private static Uniform1f uAge01;
    private static Uniform1f uTime;
    private static Uniform1f uIntensity;

    private record Explosion(double x, double y, double z, float radius, long startWorldTick, int durationTicks) {}
    private static final Long2ObjectOpenHashMap<Explosion> EXPLOSIONS = new Long2ObjectOpenHashMap<>();

    private AlienBombExplosionFx() {}

    public static void init() {
        if (inited) return;
        inited = true;

        PostWorldRenderCallback.EVENT.register(new AlienBombExplosionFx());
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc == null || mc.world == null) return;
            long now = mc.world.getTime();

            var it = EXPLOSIONS.long2ObjectEntrySet().fastIterator();
            while (it.hasNext()) {
                var entry = it.next();
                Explosion fx = entry.getValue();
                if ((now - fx.startWorldTick) >= fx.durationTicks) {
                    it.remove();
                }
            }
        });
    }

    public static void spawn(long id, double x, double y, double z, float radius, int durationTicks) {
        if (client == null || client.world == null) return;
        EXPLOSIONS.put(id, new Explosion(x, y, z, radius, client.world.getTime(), durationTicks));
    }

    private static void ensureInit() {
        if (shader != null) return;

        shader = ShaderEffectManager.getInstance().manage(POST_ID, s -> {
            if (client != null && client.getFramebuffer() instanceof ReadableDepthFramebuffer rdf) {
                s.setSamplerUniform("DepthSampler", rdf.getStillDepthMap());
            }
        });

        uInvTransform = shader.findUniformMat4("InverseTransformMatrix");
        uCameraPos = shader.findUniform3f("CameraPosition");
        uCenter = shader.findUniform3f("Center");
        uRadius = shader.findUniform1f("Radius");
        uAge01 = shader.findUniform1f("Age01");
        uTime = shader.findUniform1f("iTime");
        uIntensity = shader.findUniform1f("Intensity");
    }

    @Override
    public void onWorldRendered(Camera camera, float tickDelta, long limitTime) {
        if (client == null || client.world == null || EXPLOSIONS.isEmpty()) return;

        ensureInit();
        if (shader == null) return;

        ClientWorld world = client.world;
        long now = world.getTime();

        var camPos = camera.getPos();
        uInvTransform.set(GlMatrices.getInverseTransformMatrix(tmpMat));
        uCameraPos.set((float) camPos.x, (float) camPos.y, (float) camPos.z);
        uTime.set((client.player != null ? client.player.age : 0 + tickDelta) / 20.0f);

        var it = EXPLOSIONS.long2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            var entry = it.next();
            Explosion fx = entry.getValue();

            float age01 = ((now - fx.startWorldTick) + tickDelta) / (float) fx.durationTicks;
            if (age01 >= 1.0f) {
                it.remove();
                continue;
            }

            age01 = MathHelper.clamp(age01, 0.0f, 1.0f);
            float fadeIn = smoothstep(0.00f, 0.10f, age01);
            float fadeOut = smoothstep(0.00f, 0.24f, 1.0f - age01);
            float intensity = fadeIn * fadeOut;

            uCenter.set((float) fx.x, (float) fx.y, (float) fx.z);
            uRadius.set(fx.radius);
            uAge01.set(age01);
            uIntensity.set(intensity);

            shader.render(tickDelta);
        }
    }

    private static float smoothstep(float a, float b, float x) {
        float t = MathHelper.clamp((x - a) / (b - a), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }
}