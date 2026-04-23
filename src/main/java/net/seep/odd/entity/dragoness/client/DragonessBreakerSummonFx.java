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
import net.minecraft.util.math.Vec3d;
import net.seep.odd.entity.dragoness.DragonessAttackType;
import net.seep.odd.entity.dragoness.DragonessEntity;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public final class DragonessBreakerSummonFx implements PostWorldRenderCallback {
    private static final Identifier POST_ID = new Identifier("odd", "shaders/post/dragoness_laser_block.json");
    private static final MinecraftClient CLIENT = MinecraftClient.getInstance();
    private static final Matrix4f TMP = new Matrix4f();

    private static boolean inited = false;
    private static ManagedShaderEffect shader;
    private static UniformMat4 uInvTransform;
    private static Uniform3f uCameraPos;
    private static Uniform3f uCenter;
    private static Uniform1f uHalfSize;
    private static Uniform1f uHeight;
    private static Uniform1f uCharge01;
    private static Uniform1f uIntensity;
    private static Uniform1f uTime;

    private DragonessBreakerSummonFx() {}

    public static void init() {
        if (inited) return;
        inited = true;
        PostWorldRenderCallback.EVENT.register(new DragonessBreakerSummonFx());
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
        uHalfSize = shader.findUniform1f("HalfSize");
        uHeight = shader.findUniform1f("Height");
        uCharge01 = shader.findUniform1f("Charge01");
        uIntensity = shader.findUniform1f("Intensity");
        uTime = shader.findUniform1f("iTime");
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
        uHalfSize.set(1.05f);
        uHeight.set(0.50f);
        uIntensity.set(0.95f);

        Box range = CLIENT.player.getBoundingBox().expand(160.0D);
        for (DragonessEntity dragoness : CLIENT.world.getEntitiesByClass(DragonessEntity.class, range, e -> e.isAlive() && !e.isRemoved())) {
            if (dragoness.getAttackType() != DragonessAttackType.BREAKER) continue;
            if (dragoness.getAttackTicks() < DragonessEntity.BREAKER_SUMMON_TICK || dragoness.getAttackTicks() > DragonessEntity.BREAKER_CRASH_START_TICK) continue;

            float charge01 = (dragoness.getAttackTicks() - DragonessEntity.BREAKER_SUMMON_TICK + tickDelta)
                    / (float) Math.max(1, DragonessEntity.BREAKER_CRASH_START_TICK - DragonessEntity.BREAKER_SUMMON_TICK);
            for (Vec3d center : dragoness.getBreakerSummonCenters()) {
                uCenter.set((float) center.x, (float) center.y, (float) center.z);
                uCharge01.set(charge01);
                shader.render(tickDelta);
            }
        }
    }
}
