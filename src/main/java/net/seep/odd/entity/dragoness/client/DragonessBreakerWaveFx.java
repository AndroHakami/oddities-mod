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
import net.seep.odd.entity.dragoness.DragonessAttackType;
import net.seep.odd.entity.dragoness.DragonessEntity;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public final class DragonessBreakerWaveFx implements PostWorldRenderCallback {
    private static final Identifier POST_ID = new Identifier("odd", "shaders/post/dragoness_breaker_wave.json");
    private static final MinecraftClient CLIENT = MinecraftClient.getInstance();
    private static final Matrix4f TMP = new Matrix4f();

    private static boolean inited = false;
    private static ManagedShaderEffect shader;
    private static UniformMat4 uInvTransform;
    private static Uniform3f uCameraPos;
    private static Uniform3f uCenter;
    private static Uniform1f uRadius;
    private static Uniform1f uAge01;
    private static Uniform1f uTime;
    private static Uniform1f uIntensity;

    private DragonessBreakerWaveFx() {}

    public static void init() {
        if (inited) return;
        inited = true;
        PostWorldRenderCallback.EVENT.register(new DragonessBreakerWaveFx());
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

        Box range = CLIENT.player.getBoundingBox().expand(192.0D);
        for (DragonessEntity dragoness : CLIENT.world.getEntitiesByClass(
                DragonessEntity.class,
                range,
                e -> e.isAlive() && !e.isRemoved()
        )) {
            DragonessAttackType type = dragoness.getAttackType();

            if (type == DragonessAttackType.BREAKER && dragoness.getAttackTicks() <= 10) {
                float age01 = MathHelper.clamp((dragoness.getAttackTicks() + tickDelta) / 10.0f, 0.0f, 1.0f);
                Vec3d center = dragoness.getPos().add(0.0D, 1.0D, 0.0D);

                uCenter.set((float) center.x, (float) center.y, (float) center.z);
                uRadius.set(7.0f);
                uAge01.set(age01);
                uIntensity.set(0.95f);
                shader.render(tickDelta);
            }

            if (type == DragonessAttackType.BREAKER
                    && dragoness.getAttackTicks() >= DragonessEntity.BREAKER_SUMMON_TICK - 6
                    && dragoness.getAttackTicks() < DragonessEntity.BREAKER_SUMMON_TICK + 2) {

                float age01 = MathHelper.clamp(
                        (dragoness.getAttackTicks() - (DragonessEntity.BREAKER_SUMMON_TICK - 6) + tickDelta) / 8.0f,
                        0.0f,
                        1.0f
                );

                for (Vec3d pos : dragoness.getBreakerSummonCenters()) {
                    uCenter.set((float) pos.x, (float) pos.y, (float) pos.z);
                    uRadius.set(1.2f);
                    uAge01.set(age01);
                    uIntensity.set(0.85f);
                    shader.render(tickDelta);
                }
            }
        }
    }
}