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
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Environment(EnvType.CLIENT)
public final class DragonessLaserBeamFx implements PostWorldRenderCallback {
    private static final Identifier POST_ID = new Identifier("odd", "shaders/post/dragoness_sky_laser.json");
    private static final MinecraftClient CLIENT = MinecraftClient.getInstance();
    private static final Matrix4f TMP = new Matrix4f();

    private static final Map<Long, Beam> BEAMS = new HashMap<>();
    private static boolean inited = false;

    private static ManagedShaderEffect shader;
    private static UniformMat4 uInvTransform;
    private static Uniform3f uCameraPos;
    private static Uniform3f uBeamStart;
    private static Uniform3f uBeamEnd;
    private static Uniform1f uRadius;
    private static Uniform1f uAge01;
    private static Uniform1f uIntensity;
    private static Uniform1f uTime;

    private record Beam(Vec3d start, Vec3d end, float radius, long startTick, int durationTicks) {}

    private DragonessLaserBeamFx() {}

    public static void init() {
        if (inited) return;
        inited = true;

        PostWorldRenderCallback.EVENT.register(new DragonessLaserBeamFx());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client == null || client.world == null) return;
            long now = client.world.getTime();
            Iterator<Map.Entry<Long, Beam>> it = BEAMS.entrySet().iterator();
            while (it.hasNext()) {
                Beam beam = it.next().getValue();
                if ((now - beam.startTick) >= beam.durationTicks) {
                    it.remove();
                }
            }
        });
    }

    public static void spawn(long key, Vec3d start, Vec3d end, float radius, int durationTicks) {
        if (CLIENT == null || CLIENT.world == null) return;
        BEAMS.put(key, new Beam(start, end, radius, CLIENT.world.getTime(), durationTicks));
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
        uBeamStart = shader.findUniform3f("BeamStart");
        uBeamEnd = shader.findUniform3f("BeamEnd");
        uRadius = shader.findUniform1f("Radius");
        uAge01 = shader.findUniform1f("Age01");
        uIntensity = shader.findUniform1f("Intensity");
        uTime = shader.findUniform1f("iTime");
    }

    @Override
    public void onWorldRendered(Camera camera, float tickDelta, long limitTime) {
        if (CLIENT == null || CLIENT.world == null || CLIENT.player == null || BEAMS.isEmpty()) return;

        ensureInit();
        if (shader == null) return;

        Vec3d camPos = camera.getPos();
        uInvTransform.set(GlMatrices.getInverseTransformMatrix(TMP));
        uCameraPos.set((float) camPos.x, (float) camPos.y, (float) camPos.z);
        uTime.set((CLIENT.player.age + tickDelta) / 20.0f);

        long now = CLIENT.world.getTime();
        Iterator<Map.Entry<Long, Beam>> it = BEAMS.entrySet().iterator();
        while (it.hasNext()) {
            Beam beam = it.next().getValue();
            float age01 = ((now - beam.startTick) + tickDelta) / (float) beam.durationTicks;
            if (age01 >= 1.0f) {
                it.remove();
                continue;
            }

            age01 = MathHelper.clamp(age01, 0.0f, 1.0f);
            float intensity = (1.0f - age01) * (1.0f - age01);

            uBeamStart.set((float) beam.start.x, (float) beam.start.y, (float) beam.start.z);
            uBeamEnd.set((float) beam.end.x, (float) beam.end.y, (float) beam.end.z);
            uRadius.set(beam.radius);
            uAge01.set(age01);
            uIntensity.set(intensity);
            shader.render(tickDelta);
        }
    }
}
