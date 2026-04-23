package net.seep.odd.entity.ufo.client;

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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.entity.ufo.OuterMechEntity;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public final class OuterMechBeamFx implements PostWorldRenderCallback {
    private OuterMechBeamFx() {}

    private static final Identifier POST_ID = new Identifier("odd", "shaders/post/outer_mech_beam.json");
    private static final MinecraftClient client = MinecraftClient.getInstance();
    private static final Matrix4f tmpMat = new Matrix4f();
    private static boolean inited = false;

    private static ManagedShaderEffect shader;
    private static UniformMat4 uInvTransform;
    private static Uniform3f uCameraPos;
    private static Uniform3f uBeamStart;
    private static Uniform3f uBeamEnd;
    private static Uniform1f uStartRadius;
    private static Uniform1f uEndRadius;
    private static Uniform1f uIntensity;
    private static Uniform1f uTime;

    public static void init() {
        if (inited) return;
        inited = true;
        PostWorldRenderCallback.EVENT.register(new OuterMechBeamFx());
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
        uBeamStart = shader.findUniform3f("BeamStart");
        uBeamEnd = shader.findUniform3f("BeamEnd");
        uStartRadius = shader.findUniform1f("StartRadius");
        uEndRadius = shader.findUniform1f("EndRadius");
        uIntensity = shader.findUniform1f("Intensity");
        uTime = shader.findUniform1f("iTime");
    }

    @Override
    public void onWorldRendered(Camera camera, float tickDelta, long limitTime) {
        if (client == null || client.world == null || client.player == null) return;

        ensureInit();
        if (shader == null) return;

        var camPos = camera.getPos();
        uInvTransform.set(GlMatrices.getInverseTransformMatrix(tmpMat));
        uCameraPos.set((float) camPos.x, (float) camPos.y, (float) camPos.z);
        uTime.set(((client.player.age) + tickDelta) / 20.0f);

        Box range = client.player.getBoundingBox().expand(224.0);
        for (OuterMechEntity mech : client.world.getEntitiesByClass(
                OuterMechEntity.class, range, e -> e.isAlive() && !e.isRemoved()
        )) {
            float alpha = mech.getBeamAlpha();
            if (!mech.isExtenderBeamActive() || alpha <= 0.01f) continue;

            Vec3d start = mech.getRailGunOrigin();
            Vec3d end = mech.getBeamEnd();

            uBeamStart.set((float) start.x, (float) start.y, (float) start.z);
            uBeamEnd.set((float) end.x, (float) end.y, (float) end.z);
            uStartRadius.set(1.0f);
            uEndRadius.set(7.0f);
            uIntensity.set(alpha);

            shader.render(tickDelta);
        }
    }
}