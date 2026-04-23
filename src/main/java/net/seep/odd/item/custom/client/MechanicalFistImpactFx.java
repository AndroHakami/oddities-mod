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
import net.seep.odd.item.custom.MechanicalFistItem;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public final class MechanicalFistImpactFx implements PostWorldRenderCallback {
    private MechanicalFistImpactFx() {}

    private static final Identifier POST_ID = new Identifier("odd", "shaders/post/mechanical_fist_impact.json");

    private static final MinecraftClient CLIENT = MinecraftClient.getInstance();
    private static final Matrix4f TMP_MAT = new Matrix4f();

    private static boolean inited = false;

    private static ManagedShaderEffect shader;
    private static UniformMat4 uInvTransform;
    private static Uniform3f uCameraPos;
    private static Uniform3f uOrigin;
    private static Uniform3f uDirection;
    private static Uniform1f uLength;
    private static Uniform1f uRadius;
    private static Uniform1f uProgress;
    private static Uniform1f uActive;
    private static Uniform1f uTime;
    private static Uniform1f uIntensity;

    private static Impact currentImpact;
    private static long lastProcessedUntilMain = Long.MIN_VALUE;
    private static long lastProcessedUntilOff = Long.MIN_VALUE;

    private record Impact(Vec3d origin, Vec3d direction, int ageTicks, int lifetimeTicks) {}

    public static void init() {
        if (inited) return;
        inited = true;

        MechanicalFistChargeSound.init();
        PostWorldRenderCallback.EVENT.register(new MechanicalFistImpactFx());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client == null || client.world == null) {
                currentImpact = null;
                lastProcessedUntilMain = Long.MIN_VALUE;
                lastProcessedUntilOff = Long.MIN_VALUE;
                return;
            }

            tickImpact();
            pollForNewImpact(client);
        });
    }

    private static void tickImpact() {
        if (currentImpact == null) return;

        int nextAge = currentImpact.ageTicks() + 1;
        if (nextAge >= currentImpact.lifetimeTicks()) {
            currentImpact = null;
        } else {
            currentImpact = new Impact(
                    currentImpact.origin(),
                    currentImpact.direction(),
                    nextAge,
                    currentImpact.lifetimeTicks()
            );
        }
    }

    private static void pollForNewImpact(MinecraftClient client) {
        PlayerEntity player = client.player;
        if (player == null) return;

        checkStack(player.getMainHandStack(), true);
        checkStack(player.getOffHandStack(), false);
    }

    private static void checkStack(ItemStack stack, boolean mainHand) {
        long seen = mainHand ? lastProcessedUntilMain : lastProcessedUntilOff;

        if (!(stack.getItem() instanceof MechanicalFistItem) || !stack.hasNbt() || CLIENT.world == null) {
            if (mainHand) {
                lastProcessedUntilMain = Long.MIN_VALUE;
            } else {
                lastProcessedUntilOff = Long.MIN_VALUE;
            }
            return;
        }

        long until = stack.getNbt().getLong(MechanicalFistItem.IMPACT_FX_UNTIL_NBT);
        if (until <= CLIENT.world.getTime() || until <= seen) {
            return;
        }

        Vec3d origin = new Vec3d(
                stack.getNbt().getDouble(MechanicalFistItem.IMPACT_ORIGIN_X_NBT),
                stack.getNbt().getDouble(MechanicalFistItem.IMPACT_ORIGIN_Y_NBT),
                stack.getNbt().getDouble(MechanicalFistItem.IMPACT_ORIGIN_Z_NBT)
        );

        Vec3d direction = new Vec3d(
                stack.getNbt().getDouble(MechanicalFistItem.IMPACT_DIR_X_NBT),
                stack.getNbt().getDouble(MechanicalFistItem.IMPACT_DIR_Y_NBT),
                stack.getNbt().getDouble(MechanicalFistItem.IMPACT_DIR_Z_NBT)
        );

        if (direction.lengthSquared() < 1.0E-6D) {
            direction = new Vec3d(0.0D, 0.0D, 1.0D);
        }

        currentImpact = new Impact(origin, direction.normalize(), 0, 8);

        if (mainHand) {
            lastProcessedUntilMain = until;
        } else {
            lastProcessedUntilOff = until;
        }
    }

    private static void ensureShader() {
        if (shader != null) return;

        shader = ShaderEffectManager.getInstance().manage(POST_ID, s -> {
            if (CLIENT != null && CLIENT.getFramebuffer() instanceof ReadableDepthFramebuffer rdf) {
                s.setSamplerUniform("DepthSampler", rdf.getStillDepthMap());
            }
        });

        uInvTransform = shader.findUniformMat4("InverseTransformMatrix");
        uCameraPos = shader.findUniform3f("CameraPosition");
        uOrigin = shader.findUniform3f("Origin");
        uDirection = shader.findUniform3f("Direction");
        uLength = shader.findUniform1f("Length");
        uRadius = shader.findUniform1f("Radius");
        uProgress = shader.findUniform1f("Progress01");
        uActive = shader.findUniform1f("Active01");
        uTime = shader.findUniform1f("iTime");
        uIntensity = shader.findUniform1f("Intensity");
    }

    @Override
    public void onWorldRendered(Camera camera, float tickDelta, long limitTime) {
        if (CLIENT == null || CLIENT.world == null || currentImpact == null) return;

        ensureShader();
        if (shader == null) return;

        Vec3d camPos = camera.getPos();
        Vec3d dir = currentImpact.direction().normalize();

        float progress = MathHelper.clamp(
                (currentImpact.ageTicks() + tickDelta) / (float) currentImpact.lifetimeTicks(),
                0.0F, 1.0F
        );

        float fadeIn = smoothstep(0.0F, 0.12F, progress);
        float punch = 1.0F - smoothstep(0.52F, 1.0F, progress);
        float snap = 1.0F - Math.abs(progress - 0.18F) / 0.18F;
        float intensity = fadeIn * punch * MathHelper.clamp(0.55F + Math.max(0.0F, snap) * 0.85F, 0.0F, 1.0F);
        if (intensity <= 0.001F) return;

        uInvTransform.set(GlMatrices.getInverseTransformMatrix(TMP_MAT));
        uCameraPos.set((float) camPos.x, (float) camPos.y, (float) camPos.z);
        uOrigin.set((float) currentImpact.origin().x, (float) currentImpact.origin().y, (float) currentImpact.origin().z);
        uDirection.set((float) dir.x, (float) dir.y, (float) dir.z);
        uLength.set(4.4F);
        uRadius.set(1.45F);
        uProgress.set(progress);
        uActive.set(1.0F);
        uTime.set(((CLIENT.player != null ? CLIENT.player.age : 0) + tickDelta) / 20.0F);
        uIntensity.set(2.2F * intensity);

        shader.render(tickDelta);
    }

    private static float smoothstep(float a, float b, float x) {
        float t = MathHelper.clamp((x - a) / (b - a), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }
}
