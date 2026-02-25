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

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public final class LifeAuroraFx implements PostWorldRenderCallback {
    private LifeAuroraFx() {}

    private static final Identifier POST_ID = new Identifier("odd", "shaders/post/life_aurora.json");
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

    private record Aura(double x, double y, double z,
                        float radius, float height,
                        long startWorldTick, int durationTicks) {}

    private static final Long2ObjectOpenHashMap<Aura> AURAS = new Long2ObjectOpenHashMap<>();

    public static void init() {
        if (inited) return;
        inited = true;

        PostWorldRenderCallback.EVENT.register(new LifeAuroraFx());

        // ✅ cleanup on real client ticks, not per frame
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc == null || mc.world == null) return;
            long now = mc.world.getTime();

            var it = AURAS.long2ObjectEntrySet().fastIterator();
            while (it.hasNext()) {
                var e = it.next();
                Aura a = e.getValue();
                if ((now - a.startWorldTick) >= a.durationTicks) it.remove();
            }
        });
    }

    /** Called by S2C packet */
    public static void spawn(long id, double x, double y, double z, float radius, int durationTicks) {
        if (client == null || client.world == null) return;

        // ✅ 7 blocks tall requested
        float height = 7.0f;

        // refresh same id if re-sent
        AURAS.put(id, new Aura(x, y, z, radius, height, client.world.getTime(), durationTicks));
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
        if (client == null || client.world == null || AURAS.isEmpty()) return;

        ensureInit();
        if (shader == null) return;

        ClientWorld world = client.world;
        long now = world.getTime();

        // common uniforms
        var camPos = camera.getPos();
        uInvTransform.set(GlMatrices.getInverseTransformMatrix(tmpMat));
        uCameraPos.set((float) camPos.x, (float) camPos.y, (float) camPos.z);

        float time = ((client.player != null ? client.player.age : 0) + tickDelta) / 20.0f;
        uTime.set(time);

        // render each aura (no ticking here)
        var it = AURAS.long2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            var e = it.next();
            Aura a = e.getValue();

            float age01 = (float)((now - a.startWorldTick) + tickDelta) / (float)a.durationTicks;
            if (age01 >= 1.0f) { it.remove(); continue; }
            age01 = MathHelper.clamp(age01, 0f, 1f);

            // erupt + dissipate curve
            float fadeIn  = smoothstep(0.00f, 0.16f, age01);
            float fadeOut = smoothstep(0.00f, 0.20f, 1.0f - age01);
            float intensity = fadeIn * fadeOut;

            uCenter.set((float)a.x, (float)a.y, (float)a.z);
            uRadius.set(a.radius);
            uHeight.set(a.height);

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
