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
public final class OuterMechBulletFx implements PostWorldRenderCallback {
    private OuterMechBulletFx() {}

    private static final Identifier POST_ID = new Identifier("odd", "shaders/post/outer_mech_bullets.json");
    private static final MinecraftClient client = MinecraftClient.getInstance();
    private static final Matrix4f TMP = new Matrix4f();
    private static boolean inited = false;

    private static ManagedShaderEffect shader;
    private static UniformMat4 uInvTransform;
    private static Uniform3f uCameraPos;
    private static Uniform3f uStart;
    private static Uniform3f uEnd;
    private static Uniform1f uRadius;
    private static Uniform1f uIntensity;
    private static Uniform1f uTime;

    public static void init() {
        if (inited) return;
        inited = true;
        PostWorldRenderCallback.EVENT.register(new OuterMechBulletFx());
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
        uStart = shader.findUniform3f("TraceStart");
        uEnd = shader.findUniform3f("TraceEnd");
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
        uInvTransform.set(GlMatrices.getInverseTransformMatrix(TMP));
        uCameraPos.set((float) camPos.x, (float) camPos.y, (float) camPos.z);
        uTime.set(((client.player.age) + tickDelta) / 20.0f);

        Box range = client.player.getBoundingBox().expand(220.0);
        for (OuterMechEntity mech : client.world.getEntitiesByClass(
                OuterMechEntity.class, range, e -> e.isAlive() && !e.isRemoved()
        )) {
            float alpha = mech.getBulletAlpha();
            if (alpha <= 0.01f) continue;

            Vec3d s = mech.getBulletStart();
            Vec3d e = mech.getBulletEnd();
            Vec3d dir = e.subtract(s).normalize();
            Vec3d right = new Vec3d(-dir.z, 0.0, dir.x);
            if (right.lengthSquared() < 1.0E-4) right = new Vec3d(1.0, 0.0, 0.0);
            right = right.normalize();
            Vec3d up = right.crossProduct(dir).normalize();

            for (int i = -1; i <= 1; i++) {
                Vec3d off = right.multiply(i * 0.12).add(up.multiply(i * 0.03));
                Vec3d rs = s.add(off);
                Vec3d re = e.add(off.multiply(1.3));

                uStart.set((float) rs.x, (float) rs.y, (float) rs.z);
                uEnd.set((float) re.x, (float) re.y, (float) re.z);
                uRadius.set(0.15f);
                uIntensity.set(alpha * (i == 0 ? 1.0f : 0.75f));
                shader.render(tickDelta);
            }
        }
    }
}