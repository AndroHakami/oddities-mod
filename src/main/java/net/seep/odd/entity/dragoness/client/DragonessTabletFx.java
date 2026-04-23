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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.entity.dragoness.DragonessEntity;
import net.seep.odd.entity.dragoness.DragonessAttackType;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public final class DragonessTabletFx implements PostWorldRenderCallback {
    private static final Identifier POST_ID = new Identifier("odd", "shaders/post/dragoness_tablet_holo.json");
    private static final MinecraftClient CLIENT = MinecraftClient.getInstance();
    private static final Matrix4f TMP = new Matrix4f();

    private static boolean inited = false;

    private static ManagedShaderEffect shader;
    private static UniformMat4 uInvTransform;
    private static Uniform3f uCameraPos;
    private static Uniform3f uOrigin;
    private static Uniform3f uNormal;
    private static Uniform1f uPlaneHalfWidth;
    private static Uniform1f uPlaneHalfHeight;
    private static Uniform1f uPlaneThickness;
    private static Uniform1f uActive01;
    private static Uniform1f uTime;
    private static Uniform1f uIntensity;
    private static Uniform3f uFrontTint;
    private static Uniform3f uBackTint;

    private DragonessTabletFx() {}

    public static void init() {
        if (inited) return;
        inited = true;
        PostWorldRenderCallback.EVENT.register(new DragonessTabletFx());
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
        uOrigin = shader.findUniform3f("Origin");
        uNormal = shader.findUniform3f("Normal");
        uPlaneHalfWidth = shader.findUniform1f("PlaneHalfWidth");
        uPlaneHalfHeight = shader.findUniform1f("PlaneHalfHeight");
        uPlaneThickness = shader.findUniform1f("PlaneThickness");
        uActive01 = shader.findUniform1f("Active01");
        uTime = shader.findUniform1f("iTime");
        uIntensity = shader.findUniform1f("Intensity");
        uFrontTint = shader.findUniform3f("FrontTint");
        uBackTint = shader.findUniform3f("BackTint");
    }

    @Override
    public void onWorldRendered(Camera camera, float tickDelta, long limitTime) {
        if (CLIENT == null || CLIENT.world == null || CLIENT.player == null) return;

        ensureInit();
        if (shader == null) return;

        Box range = CLIENT.player.getBoundingBox().expand(160.0D);
        Vec3d camPos = camera.getPos();
        uInvTransform.set(GlMatrices.getInverseTransformMatrix(TMP));
        uCameraPos.set((float) camPos.x, (float) camPos.y, (float) camPos.z);
        uTime.set((CLIENT.player.age + tickDelta) / 20.0f);
        uPlaneHalfWidth.set(1.55f);
        uPlaneHalfHeight.set(0.95f);
        uPlaneThickness.set(0.10f);
        uFrontTint.set(0.12f, 1.00f, 0.30f);
        uBackTint.set(0.04f, 0.60f, 0.18f);

        for (DragonessEntity dragoness : CLIENT.world.getEntitiesByClass(
                DragonessEntity.class,
                range,
                e -> e.isAlive() && !e.isRemoved()
        )) {
            if (dragoness.getAttackType() != DragonessAttackType.LASER) continue;

            float charge01 = MathHelper.clamp((dragoness.getAttackTicks() + tickDelta) / (float) DragonessEntity.LASER_CAST_TICK, 0.0f, 1.0f);
            if (charge01 >= 1.0f) continue;

            Vec3d forward = dragoness.getRotationVec(tickDelta).normalize();
            Vec3d origin = dragoness.getPos()
                    .add(0.0D, 4.15D, 0.0D)
                    .add(forward.multiply(2.35D))
                    .add(0.40D, 0.25D, 0.0D);

            uOrigin.set((float) origin.x, (float) origin.y, (float) origin.z);
            uNormal.set((float) forward.x, (float) forward.y * 0.15f, (float) forward.z);
            uActive01.set(charge01);
            uIntensity.set(0.82f);
            shader.render(tickDelta);
        }
    }
}
