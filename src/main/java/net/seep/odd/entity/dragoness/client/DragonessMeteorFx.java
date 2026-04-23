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
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.entity.dragoness.DragonessAttackType;
import net.seep.odd.entity.dragoness.DragonessEntity;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public final class DragonessMeteorFx implements PostWorldRenderCallback {
    private static final Identifier POST_ID = new Identifier("odd", "shaders/post/dragoness_meteor_fall.json");
    private static final MinecraftClient CLIENT = MinecraftClient.getInstance();
    private static final Matrix4f TMP = new Matrix4f();

    private static boolean inited = false;
    private static ManagedShaderEffect shader;
    private static UniformMat4 uInvTransform;
    private static Uniform3f uCameraPos;
    private static Uniform3f uCenter;
    private static Uniform3f uTrailStart;
    private static Uniform3f uTrailEnd;
    private static Uniform1f uRadius;
    private static Uniform1f uAge01;
    private static Uniform1f uTime;
    private static Uniform1f uIntensity;

    private DragonessMeteorFx() {}

    public static void init() {
        if (inited) return;
        inited = true;
        PostWorldRenderCallback.EVENT.register(new DragonessMeteorFx());
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
        uTrailStart = shader.findUniform3f("TrailStart");
        uTrailEnd = shader.findUniform3f("TrailEnd");
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
        for (DragonessEntity dragoness : CLIENT.world.getEntitiesByClass(DragonessEntity.class, range, e -> e.isAlive() && !e.isRemoved())) {
            int targetId = dragoness.getMeteorTargetId();
            int meteorTicks = dragoness.getMeteorTicks();
            if (targetId >= 0 && meteorTicks > 0) {
                Entity entity = CLIENT.world.getEntityById(targetId);
                if (entity != null && !entity.isRemoved()) {
                    renderMeteor(entity.getPos().add(0.0D, entity.getHeight() * 0.52D, 0.0D), entity.getVelocity(), 1.0f - Math.min(1.0f, meteorTicks / 80.0f), 1.05f, 0.72f, tickDelta);
                }
            }

            DragonessAttackType type = dragoness.getAttackType();
            boolean selfMeteor = (type == DragonessAttackType.BREAKER && dragoness.getAttackTicks() >= DragonessEntity.BREAKER_CRASH_START_TICK)
                    || (type == DragonessAttackType.CRASH_DOWN && dragoness.getAttackTicks() >= DragonessEntity.CRASH_DIVE_START_TICK)
                    || type == DragonessAttackType.COMBO_FLY_DOWN;
            if (selfMeteor) {
                Vec3d center = dragoness.getPos().add(0.0D, dragoness.getHeight() * 0.65D, 0.0D);
                Vec3d motion = dragoness.getVelocity();
                float age01 = MathHelper.clamp(dragoness.getAttackTicks() / 20.0f, 0.0f, 1.0f);
                renderMeteor(center, motion, age01, 1.2f, 0.78f, tickDelta);
            }
        }
    }

    private void renderMeteor(Vec3d center, Vec3d motion, float age01, float radius, float intensity, float tickDelta) {
        if (motion.lengthSquared() < 0.0004D) {
            motion = new Vec3d(0.0D, -1.0D, 0.0D);
        }
        Vec3d dir = motion.normalize();
        Vec3d trailStart = center.subtract(dir.multiply(7.5D)).add(0.0D, 1.0D, 0.0D);
        uCenter.set((float) center.x, (float) center.y, (float) center.z);
        uTrailStart.set((float) trailStart.x, (float) trailStart.y, (float) trailStart.z);
        uTrailEnd.set((float) center.x, (float) center.y, (float) center.z);
        uRadius.set(radius);
        uAge01.set(age01);
        uIntensity.set(intensity);
        shader.render(tickDelta);
    }
}
