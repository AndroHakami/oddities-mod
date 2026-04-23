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
public final class OuterMechWarmupFx implements PostWorldRenderCallback {
    private OuterMechWarmupFx() {}

    private static final Identifier POST_ID = new Identifier("odd", "shaders/post/outer_mech_warmup.json");
    private static final MinecraftClient client = MinecraftClient.getInstance();
    private static final Matrix4f tmpMat = new Matrix4f();
    private static boolean inited = false;

    private static ManagedShaderEffect shader;
    private static UniformMat4 uInvTransform;
    private static Uniform3f uCameraPos;
    private static Uniform3f uCenter;
    private static Uniform1f uRadius;
    private static Uniform1f uIntensity;
    private static Uniform1f uTime;

    public static void init() {
        if (inited) return;
        inited = true;
        PostWorldRenderCallback.EVENT.register(new OuterMechWarmupFx());
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

        Box range = client.player.getBoundingBox().expand(192.0);
        for (OuterMechEntity mech : client.world.getEntitiesByClass(
                OuterMechEntity.class, range, e -> e.isAlive() && !e.isRemoved()
        )) {
            float alpha = mech.getWarmupAlpha();
            if (alpha <= 0.01f) continue;

            Vec3d core = mech.getRailGunOrigin().add(mech.bodyForward().multiply(1.10));
            float mainRadius = 0.40f + alpha * 1.35f;

            // main orb
            uCenter.set((float) core.x, (float) core.y, (float) core.z);
            uRadius.set(mainRadius);
            uIntensity.set(alpha);
            shader.render(tickDelta);

            // collecting orbiters
            int count = 5;
            double time = (client.player.age + tickDelta) * 0.12;
            for (int i = 0; i < count; i++) {
                double ang = time + (Math.PI * 2.0 * i / count);
                double radius = (1.55 - alpha * 1.15) + Math.sin(time * 1.7 + i) * 0.08;
                Vec3d off = new Vec3d(Math.cos(ang) * radius, Math.sin(ang * 1.8) * 0.42, Math.sin(ang) * radius);
                Vec3d c = core.add(off);

                uCenter.set((float) c.x, (float) c.y, (float) c.z);
                uRadius.set(0.10f + (1.0f - alpha) * 0.18f);
                uIntensity.set(alpha * 0.65f);
                shader.render(tickDelta);
            }
        }
    }
}