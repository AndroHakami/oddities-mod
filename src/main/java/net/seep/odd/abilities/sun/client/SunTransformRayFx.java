package net.seep.odd.abilities.sun.client;

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
public final class SunTransformRayFx implements PostWorldRenderCallback {
    private SunTransformRayFx() {}

    private static final Identifier POST_ID = new Identifier("odd", "shaders/post/sun_transform_ray.json");
    private static final MinecraftClient client = MinecraftClient.getInstance();
    private static final Matrix4f TMP = new Matrix4f();

    private static boolean inited = false;
    private static ManagedShaderEffect shader;
    private static UniformMat4 uInvTransform;
    private static Uniform3f uCameraPos;
    private static Uniform3f uStart;
    private static Uniform3f uEnd;
    private static Uniform1f uRadius;
    private static Uniform1f uAge01;
    private static Uniform1f uTime;
    private static Uniform1f uIntensity;
    private static Uniform1f uReverse;

    private record Ray(double x, double y, double z, boolean reverse, long startTick, int durationTicks) {}
    private static final Long2ObjectOpenHashMap<Ray> RAYS = new Long2ObjectOpenHashMap<>();

    public static void init() {
        if (inited) return;
        inited = true;

        PostWorldRenderCallback.EVENT.register(new SunTransformRayFx());
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc == null || mc.world == null) return;
            long now = mc.world.getTime();
            var it = RAYS.long2ObjectEntrySet().fastIterator();
            while (it.hasNext()) {
                var e = it.next();
                Ray r = e.getValue();
                if ((now - r.startTick) >= r.durationTicks) it.remove();
            }
        });
    }

    public static void spawn(long id, double x, double y, double z, boolean reverse) {
        if (client == null || client.world == null) return;
        RAYS.put(id, new Ray(x, y, z, reverse, client.world.getTime(), 14));
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
        uStart = shader.findUniform3f("Start");
        uEnd = shader.findUniform3f("End");
        uRadius = shader.findUniform1f("Radius");
        uAge01 = shader.findUniform1f("Age01");
        uTime = shader.findUniform1f("iTime");
        uIntensity = shader.findUniform1f("Intensity");
        uReverse = shader.findUniform1f("Reverse");
    }

    @Override
    public void onWorldRendered(Camera camera, float tickDelta, long limitTime) {
        if (client == null || client.world == null || RAYS.isEmpty()) return;
        ensureInit();
        if (shader == null) return;

        ClientWorld world = client.world;
        long now = world.getTime();
        var cam = camera.getPos();
        uInvTransform.set(GlMatrices.getInverseTransformMatrix(TMP));
        uCameraPos.set((float) cam.x, (float) cam.y, (float) cam.z);
        uTime.set(((client.player != null ? client.player.age : 0) + tickDelta) / 20.0f);

        var it = RAYS.long2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            var e = it.next();
            Ray ray = e.getValue();
            float age01 = (float) ((now - ray.startTick) + tickDelta) / (float) ray.durationTicks;
            if (age01 >= 1.0f) { it.remove(); continue; }
            age01 = MathHelper.clamp(age01, 0f, 1f);

            uStart.set((float) ray.x, (float) (ray.y + 22.0), (float) ray.z);
            uEnd.set((float) ray.x, (float) ray.y, (float) ray.z);
            uRadius.set(1.15f);
            uAge01.set(age01);
            uIntensity.set(1.0f);
            uReverse.set(ray.reverse ? 1.0f : 0.0f);
            shader.render(tickDelta);
        }
    }
}
