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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Environment(EnvType.CLIENT)
public final class DragonessLaserBlockFx implements PostWorldRenderCallback {
    private static final Identifier POST_ID = new Identifier("odd", "shaders/post/dragoness_laser_block.json");
    private static final MinecraftClient CLIENT = MinecraftClient.getInstance();
    private static final Matrix4f TMP = new Matrix4f();

    private static final Map<Integer, Integer> LAST_LASER_EXPLOSION_SERIAL = new HashMap<>();
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

    private DragonessLaserBlockFx() {}

    public static void init() {
        if (inited) return;
        inited = true;
        PostWorldRenderCallback.EVENT.register(new DragonessLaserBlockFx());
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
        Box range = CLIENT.player.getBoundingBox().expand(192.0D);
        for (DragonessEntity dragoness : CLIENT.world.getEntitiesByClass(
                DragonessEntity.class,
                range,
                e -> e.isAlive() && !e.isRemoved()
        )) {
            DragonessAttackType type = dragoness.getAttackType();

            if (type == DragonessAttackType.LASER) {
                uHalfSize.set(DragonessEntity.getLaserAreaHalfSize());
                uHeight.set(0.48f);
                uIntensity.set(1.0f);

                int serial = dragoness.getAttackSerial();
                List<Vec3d> strikes = dragoness.getLaserTargetCenters();
                if (strikes.isEmpty()) {
                    strikes = DragonessEntity.computeLaserTargetCenters(CLIENT.world, dragoness, true);
                }

                if (dragoness.getAttackTicks() < DragonessEntity.LASER_CAST_TICK) {
                    float charge01 = (dragoness.getAttackTicks() + tickDelta) / (float) DragonessEntity.LASER_CAST_TICK;
                    for (Vec3d strike : strikes) {
                        uCenter.set((float) strike.x, (float) strike.y, (float) strike.z);
                        uCharge01.set(charge01);
                        shader.render(tickDelta);
                    }
                } else {
                    int prev = LAST_LASER_EXPLOSION_SERIAL.getOrDefault(dragoness.getId(), -1);
                    if (serial != prev) {
                        LAST_LASER_EXPLOSION_SERIAL.put(dragoness.getId(), serial);
                        int i = 0;
                        for (Vec3d strike : strikes) {
                            long key = (((long) dragoness.getId()) << 32) ^ (((long) serial) << 12) ^ i;
                            Vec3d beamStart = strike.add(0.0D, 36.0D, 0.0D);
                            DragonessLaserBeamFx.spawn(key, beamStart, strike.add(0.0D, 0.15D, 0.0D), 0.52f, 9);
                            DragonessImpactFx.spawn(key ^ 0x5F3759DFL, strike, 7.3f, 10);
                            i++;
                        }
                    }
                }
                continue;
            }

            if (type == DragonessAttackType.CRASH_DOWN && dragoness.getAttackTicks() < DragonessEntity.CRASH_DIVE_START_TICK) {
                Vec3d strike = dragoness.getDiveIndicatorCenter();
                if (strike.y > -9990.0D) {
                    uHalfSize.set(3.2f);
                    uHeight.set(0.52f);
                    uIntensity.set(0.92f);
                    uCenter.set((float) strike.x, (float) strike.y, (float) strike.z);
                    uCharge01.set((dragoness.getAttackTicks() + tickDelta) / (float) Math.max(1, DragonessEntity.CRASH_DIVE_START_TICK));
                    shader.render(tickDelta);
                }
                continue;
            }

            if ((type == DragonessAttackType.COMBO_HIT2 && dragoness.getAttackTicks() >= 18) || type == DragonessAttackType.COMBO_FLY_DOWN) {
                Vec3d strike = dragoness.getDiveIndicatorCenter();
                if (strike.y > -9990.0D) {
                    uHalfSize.set(4.6f);
                    uHeight.set(0.56f);
                    uIntensity.set(1.0f);
                    uCenter.set((float) strike.x, (float) strike.y, (float) strike.z);
                    float charge01 = type == DragonessAttackType.COMBO_HIT2
                            ? Math.min(1.0f, (dragoness.getAttackTicks() - 18 + tickDelta) / 12.0f)
                            : 1.0f;
                    uCharge01.set(charge01);
                    shader.render(tickDelta);
                }
            }
        }
    }
}
