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
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.entity.ufo.UfoSaucerEntity;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public final class UfoLaserRayFx implements PostWorldRenderCallback {
    private static final Identifier POST_ID = new Identifier("odd", "shaders/post/ufo_laser_ray.json");

    private static boolean inited = false;
    private static final MinecraftClient client = MinecraftClient.getInstance();
    private static final Matrix4f tmpMat = new Matrix4f();

    private static ManagedShaderEffect shader;
    private static UniformMat4 uInvTransform;
    private static Uniform3f uCameraPos;
    private static Uniform3f uBeamStart;
    private static Uniform3f uBeamEnd;
    private static Uniform1f uRadius;
    private static Uniform1f uIntensity;
    private static Uniform1f uTime;

    private UfoLaserRayFx() {}

    public static void init() {
        if (inited) return;
        inited = true;
        PostWorldRenderCallback.EVENT.register(new UfoLaserRayFx());
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
        uBeamStart    = shader.findUniform3f("BeamStart");
        uBeamEnd      = shader.findUniform3f("BeamEnd");
        uRadius       = shader.findUniform1f("Radius");
        uIntensity    = shader.findUniform1f("Intensity");
        uTime         = shader.findUniform1f("iTime");
    }

    @Override
    public void onWorldRendered(Camera camera, float tickDelta, long limitTime) {
        if (client == null || client.world == null || client.player == null) return;

        ensureInit();
        if (shader == null) return;

        ClientWorld world = client.world;
        Box range = client.player.getBoundingBox().expand(192.0);

        var camPos = camera.getPos();
        uInvTransform.set(GlMatrices.getInverseTransformMatrix(tmpMat));
        uCameraPos.set((float) camPos.x, (float) camPos.y, (float) camPos.z);
        uTime.set((client.player.age + tickDelta) / 20.0f);

        for (UfoSaucerEntity saucer : world.getEntitiesByClass(UfoSaucerEntity.class, range, e -> e.isAlive() && e.hasLaserVisual())) {
            Vec3d armStart = UfoSaucerBoneTracker.getArmWorldPos(saucer.getId());
            Vec3d fallback = saucer.getPos().add(0.0, saucer.getStandingEyeHeight() * 0.7, 0.0);
            Vec3d start = armStart != null ? armStart : fallback;
            Vec3d end = saucer.getLaserVisualTarget();

            float fade = saucer.getLaserVisualAge01();
            float intensity = MathHelper.clamp(fade * fade * 1.35f, 0.0f, 1.0f);

            uBeamStart.set((float) start.x, (float) start.y, (float) start.z);
            uBeamEnd.set((float) end.x, (float) end.y, (float) end.z);
            uRadius.set(0.11f);
            uIntensity.set(intensity);

            shader.render(tickDelta);
        }
    }
}