package net.seep.odd.entity.bosswitch.client;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
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
import net.minecraft.util.math.Vec3d;
import net.seep.odd.entity.bosswitch.BossWitchSnareEntity;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public final class BossWitchSnareFx implements PostWorldRenderCallback {
    private BossWitchSnareFx() {}

    private static final Identifier POST_ID = new Identifier("odd", "shaders/post/boss_witch_snare.json");

    private static final MinecraftClient CLIENT = MinecraftClient.getInstance();
    private static final Matrix4f TMP_MAT = new Matrix4f();

    private static final Int2ObjectOpenHashMap<SnareVisual> VISUALS = new Int2ObjectOpenHashMap<>();

    private static boolean inited = false;

    private static ManagedShaderEffect shader;
    private static UniformMat4 uInvTransform;
    private static Uniform3f uCameraPos;
    private static Uniform3f uCenter;
    private static Uniform1f uRadius;
    private static Uniform1f uWarn01;
    private static Uniform1f uRoot01;
    private static Uniform1f uActive01;
    private static Uniform1f uTime;
    private static Uniform1f uIntensity;

    private record SnareVisual(Vec3d center, float ageTicks, long lastSeenTick) {}

    public static void init() {
        if (inited) return;
        inited = true;

        PostWorldRenderCallback.EVENT.register(new BossWitchSnareFx());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client == null || client.world == null || VISUALS.isEmpty()) return;

            long now = client.world.getTime();
            var it = VISUALS.int2ObjectEntrySet().fastIterator();
            while (it.hasNext()) {
                var entry = it.next();
                SnareVisual visual = entry.getValue();

                if ((now - visual.lastSeenTick()) > 2L || visual.ageTicks() >= 78.0f) {
                    it.remove();
                }
            }
        });
    }

    public static void track(BossWitchSnareEntity entity, float tickDelta) {
        init();
        if (CLIENT == null || CLIENT.world == null) return;

        Vec3d center = entity.getPos().add(0.0D, 0.05D, 0.0D);
        VISUALS.put(entity.getId(), new SnareVisual(center, entity.age + tickDelta, CLIENT.world.getTime()));
    }

    private static void ensureShader() {
        if (shader != null) return;

        shader = ShaderEffectManager.getInstance().manage(POST_ID, s -> {
            if (CLIENT != null && CLIENT.getFramebuffer() instanceof ReadableDepthFramebuffer rdf) {
                s.setSamplerUniform("DepthSampler", rdf.getStillDepthMap());
            }
        });

        uInvTransform = shader.findUniformMat4("InverseTransformMatrix");
        uCameraPos    = shader.findUniform3f("CameraPosition");
        uCenter       = shader.findUniform3f("Center");
        uRadius       = shader.findUniform1f("Radius");
        uWarn01       = shader.findUniform1f("Warn01");
        uRoot01       = shader.findUniform1f("Root01");
        uActive01     = shader.findUniform1f("Active01");
        uTime         = shader.findUniform1f("iTime");
        uIntensity    = shader.findUniform1f("Intensity");
    }

    @Override
    public void onWorldRendered(Camera camera, float tickDelta, long limitTime) {
        if (CLIENT == null || CLIENT.world == null || VISUALS.isEmpty()) return;

        ensureShader();
        if (shader == null) return;

        ClientWorld world = CLIENT.world;
        Vec3d camPos = camera.getPos();

        uInvTransform.set(GlMatrices.getInverseTransformMatrix(TMP_MAT));
        uCameraPos.set((float) camPos.x, (float) camPos.y, (float) camPos.z);
        uTime.set(((CLIENT.player != null ? CLIENT.player.age : 0) + tickDelta) / 20.0f);

        var it = VISUALS.int2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            SnareVisual visual = it.next().getValue();

            float age = visual.ageTicks();
            float warn01 = MathHelper.clamp(age / 12.0f, 0.0f, 1.0f);
            float root01 = MathHelper.clamp((age - 12.0f) / 60.0f, 0.0f, 1.0f);
            float active = age >= 12.0f && age <= 72.0f ? 1.0f : 0.0f;

            float warnGlow = smoothstep(0.0f, 0.18f, warn01) * smoothstep(0.0f, 0.35f, 1.0f - root01 * 0.35f);
            float activePulse = active * smoothstep(0.0f, 0.08f, root01) * smoothstep(0.0f, 0.10f, 1.0f - root01);
            float intensity = Math.max(warnGlow, activePulse);

            if (intensity <= 0.001f) continue;

            uCenter.set((float) visual.center().x, (float) visual.center().y, (float) visual.center().z);
            uRadius.set(3.0f);
            uWarn01.set(warn01);
            uRoot01.set(root01);
            uActive01.set(active);
            uIntensity.set(intensity);

            shader.render(tickDelta);
        }
    }

    private static float smoothstep(float a, float b, float x) {
        float t = MathHelper.clamp((x - a) / (b - a), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }
}
