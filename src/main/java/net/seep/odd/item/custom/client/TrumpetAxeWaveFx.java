// FILE: src/main/java/net/seep/odd/item/client/TrumpetAxeWaveFx.java
package net.seep.odd.item.custom.client;

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
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.item.custom.TrumpetAxeItem;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public final class TrumpetAxeWaveFx implements PostWorldRenderCallback {
    private TrumpetAxeWaveFx() {}

    private static final Identifier POST_ID = new Identifier("odd", "shaders/post/trumpet_axe_wave.json");

    private static final MinecraftClient CLIENT = MinecraftClient.getInstance();
    private static final Matrix4f TMP_MAT = new Matrix4f();

    private static boolean inited = false;

    private static ManagedShaderEffect shader;
    private static UniformMat4 uInvTransform;
    private static Uniform3f uCameraPos;
    private static Uniform3f uOrigin;
    private static Uniform3f uDirection;
    private static Uniform1f uConeLength;
    private static Uniform1f uAngleCos;
    private static Uniform1f uProgress;
    private static Uniform1f uThickness;
    private static Uniform1f uActive;
    private static Uniform1f uTime;
    private static Uniform1f uIntensity;

    private static Wave currentWave;
    private static long lastProcessedPoseUntilMain = Long.MIN_VALUE;
    private static long lastProcessedPoseUntilOff = Long.MIN_VALUE;

    private record Wave(Vec3d origin, Vec3d direction, int ageTicks, int lifetimeTicks) {}

    public static void init() {
        if (inited) return;
        inited = true;

        PostWorldRenderCallback.EVENT.register(new TrumpetAxeWaveFx());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client == null || client.world == null) {
                currentWave = null;
                lastProcessedPoseUntilMain = Long.MIN_VALUE;
                lastProcessedPoseUntilOff = Long.MIN_VALUE;
                return;
            }

            tickWave();
            pollForNewUse(client);
        });
    }

    private static void tickWave() {
        if (currentWave == null) return;

        int nextAge = currentWave.ageTicks() + 1;
        if (nextAge >= currentWave.lifetimeTicks()) {
            currentWave = null;
        } else {
            currentWave = new Wave(
                    currentWave.origin(),
                    currentWave.direction(),
                    nextAge,
                    currentWave.lifetimeTicks()
            );
        }
    }

    private static void pollForNewUse(MinecraftClient client) {
        PlayerEntity player = client.player;
        if (player == null) return;

        checkStack(player, player.getMainHandStack(), true);
        checkStack(player, player.getOffHandStack(), false);
    }

    private static void checkStack(PlayerEntity player, ItemStack stack, boolean mainHand) {
        long seen = mainHand ? lastProcessedPoseUntilMain : lastProcessedPoseUntilOff;

        if (!(stack.getItem() instanceof TrumpetAxeItem) || !stack.hasNbt() || CLIENT.world == null) {
            if (mainHand) {
                lastProcessedPoseUntilMain = Long.MIN_VALUE;
            } else {
                lastProcessedPoseUntilOff = Long.MIN_VALUE;
            }
            return;
        }

        long until = stack.getNbt().getLong(TrumpetAxeItem.BLOW_POSE_UNTIL_NBT);

        if (until > CLIENT.world.getTime() && until > seen) {
            trigger(player);

            if (mainHand) {
                lastProcessedPoseUntilMain = until;
            } else {
                lastProcessedPoseUntilOff = until;
            }
        }
    }

    private static void trigger(PlayerEntity player) {
        Vec3d dir = player.getRotationVec(1.0F).normalize();
        Vec3d origin = player.getCameraPosVec(1.0F)
                .add(dir.multiply(0.55D))
                .add(0.0D, -0.05D, 0.0D);

        // longer life so the wave is actually readable
        currentWave = new Wave(origin, dir, 0, 14);
    }

    private static void ensureShader() {
        if (shader != null) return;

        shader = ShaderEffectManager.getInstance().manage(POST_ID, s -> {
            if (CLIENT != null && CLIENT.getFramebuffer() instanceof ReadableDepthFramebuffer rdf) {
                s.setSamplerUniform("DepthSampler", rdf.getStillDepthMap());
            }
        });

        uInvTransform = shader.findUniformMat4("InverseTransformMatrix");
        uCameraPos    = shader.findUniform3f("CameraPosition");
        uOrigin       = shader.findUniform3f("Origin");
        uDirection    = shader.findUniform3f("Direction");
        uConeLength   = shader.findUniform1f("ConeLength");
        uAngleCos     = shader.findUniform1f("AngleCos");
        uProgress     = shader.findUniform1f("Progress01");
        uThickness    = shader.findUniform1f("Thickness");
        uActive       = shader.findUniform1f("Active01");
        uTime         = shader.findUniform1f("iTime");
        uIntensity    = shader.findUniform1f("Intensity");
    }

    @Override
    public void onWorldRendered(Camera camera, float tickDelta, long limitTime) {
        if (CLIENT == null || CLIENT.world == null || currentWave == null) return;

        ensureShader();
        if (shader == null) return;

        Vec3d camPos = camera.getPos();
        Vec3d dir = currentWave.direction().normalize();

        float progress = MathHelper.clamp(
                (currentWave.ageTicks() + tickDelta) / (float) currentWave.lifetimeTicks(),
                0.0F, 1.0F
        );

        float fadeIn = smoothstep(0.0F, 0.08F, progress);
        float mid = 1.0F - Math.abs(progress - 0.45F) / 0.55F;
        float fadeOut = 1.0F - smoothstep(0.76F, 1.0F, progress);
        float intensity = fadeIn * fadeOut * MathHelper.clamp(mid * 1.35F, 0.0F, 1.0F);

        if (intensity <= 0.001F) {
            return;
        }

        uInvTransform.set(GlMatrices.getInverseTransformMatrix(TMP_MAT));
        uCameraPos.set((float) camPos.x, (float) camPos.y, (float) camPos.z);
        uOrigin.set((float) currentWave.origin().x, (float) currentWave.origin().y, (float) currentWave.origin().z);
        uDirection.set((float) dir.x, (float) dir.y, (float) dir.z);

        // stronger and wider than before
        uConeLength.set(7.2F);
        uAngleCos.set((float) Math.cos(Math.toRadians(24.0D)));
        uProgress.set(progress);
        uThickness.set(0.55F);
        uActive.set(1.0F);
        uTime.set(((CLIENT.player != null ? CLIENT.player.age : 0) + tickDelta) / 20.0F);
        uIntensity.set(1.65F * intensity);

        shader.render(tickDelta);
    }

    private static float smoothstep(float a, float b, float x) {
        float t = MathHelper.clamp((x - a) / (b - a), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }
}