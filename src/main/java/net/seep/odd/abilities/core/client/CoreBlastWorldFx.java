package net.seep.odd.abilities.core.client;

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
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public final class CoreBlastWorldFx implements PostWorldRenderCallback {
    private CoreBlastWorldFx() {}

    private static final Identifier POST_ID = new Identifier("odd", "shaders/post/core_blast_world.json");
    private static final MinecraftClient CLIENT = MinecraftClient.getInstance();
    private static final Matrix4f TMP = new Matrix4f();

    private static boolean inited = false;
    private static ManagedShaderEffect shader;

    private static UniformMat4 uInvTransform;
    private static Uniform3f uCameraPos;
    private static Uniform3f uCenter;
    private static Uniform1f uRadius;
    private static Uniform1f uTime;
    private static Uniform1f uIntensity;
    private static Uniform1f uAge01;
    private static Uniform1f uHuge;

    private record Blast(double x, double y, double z, float radius, long startTick, int durationTicks, boolean huge) {}
    private static final Long2ObjectOpenHashMap<Blast> BLASTS = new Long2ObjectOpenHashMap<>();
    private static long nextId = 1L;

    public static void init() {
        if (inited) return;
        inited = true;

        PostWorldRenderCallback.EVENT.register(new CoreBlastWorldFx());

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> BLASTS.clear());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client == null || client.world == null) {
                BLASTS.clear();
                return;
            }
            long now = client.world.getTime();
            var it = BLASTS.long2ObjectEntrySet().fastIterator();
            while (it.hasNext()) {
                var e = it.next();
                Blast b = e.getValue();
                if ((now - b.startTick) >= b.durationTicks) {
                    it.remove();
                }
            }
        });
    }

    public static void spawn(double x, double y, double z, float radius, int durationTicks, boolean huge) {
        if (CLIENT == null || CLIENT.world == null) return;
        BLASTS.put(nextId++, new Blast(x, y, z, radius, CLIENT.world.getTime(), Math.max(4, durationTicks), huge));
    }

    private static void ensureInit() {
        if (shader != null) return;

        shader = ShaderEffectManager.getInstance().manage(POST_ID, s -> {
            if (CLIENT != null && CLIENT.getFramebuffer() instanceof ReadableDepthFramebuffer rdf) {
                s.setSamplerUniform("DepthSampler", rdf.getStillDepthMap());
            }
        });

        uInvTransform = shader.findUniformMat4("InverseTransformMatrix");
        uCameraPos = shader.findUniform3f("CameraPosition");
        uCenter = shader.findUniform3f("Center");
        uRadius = shader.findUniform1f("Radius");
        uTime = shader.findUniform1f("iTime");
        uIntensity = shader.findUniform1f("Intensity");
        uAge01 = shader.findUniform1f("Age01");
        uHuge = shader.findUniform1f("Huge");
    }

    @Override
    public void onWorldRendered(Camera camera, float tickDelta, long limitTime) {
        if (CLIENT == null || CLIENT.world == null || BLASTS.isEmpty()) return;

        ensureInit();
        if (shader == null) return;

        ClientWorld world = CLIENT.world;
        long now = world.getTime();

        var camPos = camera.getPos();
        uInvTransform.set(GlMatrices.getInverseTransformMatrix(TMP));
        uCameraPos.set((float) camPos.x, (float) camPos.y, (float) camPos.z);
        uTime.set((world.getTime() + tickDelta) / 20.0F);

        var it = BLASTS.long2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            var e = it.next();
            Blast b = e.getValue();

            float age01 = ((now - b.startTick) + tickDelta) / (float) b.durationTicks;
            if (age01 >= 1.0F) {
                it.remove();
                continue;
            }
            age01 = MathHelper.clamp(age01, 0.0F, 1.0F);

            float fadeIn = smoothstep(0.0F, 0.08F, age01);
            float fadeOut = smoothstep(0.0F, 0.24F, 1.0F - age01);
            float intensity = fadeIn * fadeOut;
            if (intensity <= 0.001F) continue;

            uCenter.set((float) b.x, (float) b.y, (float) b.z);
            uRadius.set(b.radius);
            uAge01.set(age01);
            uHuge.set(b.huge ? 1.0F : 0.0F);
            uIntensity.set(intensity);

            shader.render(tickDelta);
        }
    }

    private static float smoothstep(float a, float b, float x) {
        float t = MathHelper.clamp((x - a) / (b - a), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }
}
