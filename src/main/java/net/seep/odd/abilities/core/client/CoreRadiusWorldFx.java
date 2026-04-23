package net.seep.odd.abilities.core.client;

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
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public final class CoreRadiusWorldFx implements PostWorldRenderCallback {
    private CoreRadiusWorldFx() {}

    private static final Identifier POST_ID = new Identifier("odd", "shaders/post/core_radius_world.json");
    private static final MinecraftClient CLIENT = MinecraftClient.getInstance();
    private static final Matrix4f TMP = new Matrix4f();

    private static boolean inited = false;
    private static ManagedShaderEffect shader;

    private static UniformMat4 uInvTransform;
    private static Uniform3f uCameraPos;
    private static Uniform3f uCenter;
    private static Uniform1f uRadius;
    private static Uniform1f uTime;
    private static Uniform1f uIntensity;
    private static Uniform1f uAge01;

    private static int activeTicks = 0;
    private static int totalTicks = 1;
    private static float radius = 12.0F;

    public static void init() {
        if (inited) return;
        inited = true;

        PostWorldRenderCallback.EVENT.register(new CoreRadiusWorldFx());

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            activeTicks = 0;
            totalTicks = 1;
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (activeTicks > 0) activeTicks--;
        });
    }

    public static void begin(int holdTicks, float radiusIn) {
        totalTicks = Math.max(1, holdTicks);
        activeTicks = Math.max(activeTicks, holdTicks);
        radius = Math.max(1.0F, radiusIn);
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
        uTime = shader.findUniform1f("iTime");
        uIntensity = shader.findUniform1f("Intensity");
        uAge01 = shader.findUniform1f("Age01");
    }

    @Override
    public void onWorldRendered(Camera camera, float tickDelta, long limitTime) {
        if (CLIENT == null || CLIENT.world == null || CLIENT.player == null || activeTicks <= 0) return;

        ensureInit();
        if (shader == null) return;

        var camPos = camera.getPos();
        uInvTransform.set(GlMatrices.getInverseTransformMatrix(TMP));
        uCameraPos.set((float) camPos.x, (float) camPos.y, (float) camPos.z);

        float age01 = 1.0F - (((float) activeTicks - tickDelta) / Math.max(1.0F, (float) totalTicks));
        age01 = MathHelper.clamp(age01, 0.0F, 1.0F);

        float fadeIn = smoothstep(0.0F, 0.12F, age01);
        float fadeOut = smoothstep(0.0F, 0.18F, 1.0F - age01);
        float intensity = fadeIn * fadeOut;
        if (intensity <= 0.001F) return;

        float time = (CLIENT.world.getTime() + tickDelta) / 20.0F;
        uTime.set(time);
        uIntensity.set(intensity);
        uAge01.set(age01);
        uRadius.set(radius);
        uCenter.set((float) CLIENT.player.getX(), (float) (CLIENT.player.getY() + 0.02D), (float) CLIENT.player.getZ());

        shader.render(tickDelta);
    }

    private static float smoothstep(float a, float b, float x) {
        float t = MathHelper.clamp((x - a) / (b - a), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }
}
