package net.seep.odd.entity.dragoness.client;

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
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.entity.dragoness.DragonessEntity;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Environment(EnvType.CLIENT)
public final class DragonessImpactFx implements PostWorldRenderCallback {
    private static final Identifier POST_ID = new Identifier("odd", "shaders/post/dragoness_green_explosion.json");
    private static final MinecraftClient CLIENT = MinecraftClient.getInstance();
    private static final Matrix4f TMP = new Matrix4f();

    private static final Map<Long, Explosion> EXPLOSIONS = new HashMap<>();
    private static final Map<Integer, Integer> LAST_IMPACT_SERIAL = new HashMap<>();
    private static boolean inited = false;

    private static ManagedShaderEffect shader;
    private static UniformMat4 uInvTransform;
    private static Uniform3f uCameraPos;
    private static Uniform3f uCenter;
    private static Uniform1f uRadius;
    private static Uniform1f uAge01;
    private static Uniform1f uTime;
    private static Uniform1f uIntensity;

    private record Explosion(Vec3d center, float radius, long startTick, int durationTicks) {}

    private DragonessImpactFx() {}

    public static void init() {
        if (inited) return;
        inited = true;

        PostWorldRenderCallback.EVENT.register(new DragonessImpactFx());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client == null || client.world == null) return;
            long now = client.world.getTime();
            Iterator<Map.Entry<Long, Explosion>> it = EXPLOSIONS.entrySet().iterator();
            while (it.hasNext()) {
                Explosion fx = it.next().getValue();
                if ((now - fx.startTick) >= fx.durationTicks) {
                    it.remove();
                }
            }
        });
    }

    public static void spawn(long key, Vec3d center, float radius, int durationTicks) {
        if (CLIENT == null || CLIENT.world == null) return;
        EXPLOSIONS.put(key, new Explosion(center, radius, CLIENT.world.getTime(), durationTicks));
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
        uAge01 = shader.findUniform1f("Age01");
        uTime = shader.findUniform1f("iTime");
        uIntensity = shader.findUniform1f("Intensity");
    }

    @Override
    public void onWorldRendered(Camera camera, float tickDelta, long limitTime) {
        if (CLIENT == null || CLIENT.world == null || CLIENT.player == null) return;

        ensureInit();
        if (shader == null) return;

        Vec3d camPos = camera.getPos();
        uInvTransform.set(GlMatrices.getInverseTransformMatrix(TMP));
        uCameraPos.set((float) camPos.x, (float) camPos.y, (float) camPos.z);
        uTime.set((CLIENT.player.age + tickDelta) / 20.0f);

        Box range = CLIENT.player.getBoundingBox().expand(160.0D);
        for (DragonessEntity dragoness : CLIENT.world.getEntitiesByClass(
                DragonessEntity.class, range, e -> e.isAlive() && !e.isRemoved()
        )) {
            int serial = dragoness.getImpactSerial();
            int prev = LAST_IMPACT_SERIAL.getOrDefault(dragoness.getId(), -1);
            if (serial > prev) {
                LAST_IMPACT_SERIAL.put(dragoness.getId(), serial);
                long key = (((long) dragoness.getId()) << 32) ^ serial;
                spawn(key, dragoness.getImpactCenter(), dragoness.getImpactRadius(), 12);
            }
        }

        long now = CLIENT.world.getTime();
        Iterator<Map.Entry<Long, Explosion>> it = EXPLOSIONS.entrySet().iterator();
        while (it.hasNext()) {
            Explosion fx = it.next().getValue();
            float age01 = ((now - fx.startTick) + tickDelta) / (float) fx.durationTicks;
            if (age01 >= 1.0f) {
                it.remove();
                continue;
            }

            age01 = MathHelper.clamp(age01, 0.0f, 1.0f);
            float fadeIn = smoothstep(0.00f, 0.07f, age01);
            float fadeOut = smoothstep(0.00f, 0.32f, 1.0f - age01);
            float intensity = fadeIn * fadeOut;

            uCenter.set((float) fx.center.x, (float) fx.center.y, (float) fx.center.z);
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
