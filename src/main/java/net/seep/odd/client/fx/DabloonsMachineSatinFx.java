package net.seep.odd.client.fx;

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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.Oddities;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

@Environment(EnvType.CLIENT)
public final class DabloonsMachineSatinFx implements PostWorldRenderCallback {
    private DabloonsMachineSatinFx() {}

    private static final Identifier POST_ID =
            new Identifier(Oddities.MOD_ID, "shaders/post/dabloons_machine_holo.json");

    private static final MinecraftClient CLIENT = MinecraftClient.getInstance();
    private static final Matrix4f TMP_MAT = new Matrix4f();

    private static boolean inited = false;

    private static ManagedShaderEffect shader;
    private static UniformMat4 uInvTransform;
    private static Uniform3f uCameraPos;
    private static Uniform3f uOrigin;
    private static Uniform3f uNormal;
    private static Uniform1f uPlaneHalfWidth;
    private static Uniform1f uPlaneHalfHeight;
    private static Uniform1f uPlaneThickness;
    private static Uniform1f uActive;
    private static Uniform1f uTime;
    private static Uniform1f uIntensity;

    private record Submitted(Vec3d origin, Vec3d normal, float strength) {}

    private static final Map<Long, Submitted> PENDING = new HashMap<>();

    public static void init() {
        if (inited) return;
        inited = true;
        PostWorldRenderCallback.EVENT.register(new DabloonsMachineSatinFx());
    }

    public static void submit(long key, Vec3d origin, Vec3d normal, float strength) {
        if (CLIENT == null || CLIENT.world == null || strength <= 0.001f) return;
        PENDING.put(key, new Submitted(origin, normal.normalize(), strength));
    }

    private static void ensureShader() {
        if (shader != null) return;

        shader = ShaderEffectManager.getInstance().manage(POST_ID, s -> {
            if (CLIENT != null && CLIENT.getFramebuffer() instanceof ReadableDepthFramebuffer rdf) {
                s.setSamplerUniform("DepthSampler", rdf.getStillDepthMap());
            }
        });

        uInvTransform    = shader.findUniformMat4("InverseTransformMatrix");
        uCameraPos       = shader.findUniform3f("CameraPosition");
        uOrigin          = shader.findUniform3f("Origin");
        uNormal          = shader.findUniform3f("Normal");
        uPlaneHalfWidth  = shader.findUniform1f("PlaneHalfWidth");
        uPlaneHalfHeight = shader.findUniform1f("PlaneHalfHeight");
        uPlaneThickness  = shader.findUniform1f("PlaneThickness");
        uActive          = shader.findUniform1f("Active01");
        uTime            = shader.findUniform1f("iTime");
        uIntensity       = shader.findUniform1f("Intensity");
    }

    @Override
    public void onWorldRendered(Camera camera, float tickDelta, long limitTime) {
        if (CLIENT == null || CLIENT.world == null || PENDING.isEmpty()) return;

        ensureShader();
        if (shader == null) {
            PENDING.clear();
            return;
        }

        Vec3d camPos = camera.getPos();

        uInvTransform.set(GlMatrices.getInverseTransformMatrix(TMP_MAT));
        uCameraPos.set((float) camPos.x, (float) camPos.y, (float) camPos.z);
        uTime.set(((CLIENT.player != null ? CLIENT.player.age : 0) + tickDelta) / 20.0f);

        for (Submitted submitted : PENDING.values()) {
            float active = submitted.strength();
            if (active <= 0.001f) continue;

            Vec3d n = submitted.normal();

            uOrigin.set((float) submitted.origin().x, (float) submitted.origin().y, (float) submitted.origin().z);
            uNormal.set((float) n.x, (float) n.y, (float) n.z);

            uPlaneHalfWidth.set(0.52f);
            uPlaneHalfHeight.set(0.52f);
            uPlaneThickness.set(0.030f);

            uActive.set(active);

            // slight extra punch so the moving light ring reads
            uIntensity.set(0.96f + 0.24f * active);

            shader.render(tickDelta);
        }

        PENDING.clear();
    }
}