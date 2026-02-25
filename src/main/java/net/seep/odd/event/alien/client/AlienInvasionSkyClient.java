package net.seep.odd.event.alien.client;

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
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import net.seep.odd.Oddities;

import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public final class AlienInvasionSkyClient implements PostWorldRenderCallback, ClientTickEvents.EndTick {

    public static final AlienInvasionSkyClient INSTANCE = new AlienInvasionSkyClient();

    private static final Identifier POST_ID =
            new Identifier(Oddities.MOD_ID, "shaders/post/alien_overworld_sky.json");

    private final MinecraftClient client = MinecraftClient.getInstance();
    private final Matrix4f tmpMat = new Matrix4f();

    private ManagedShaderEffect shader;
    private UniformMat4 uInvTransform;
    private Uniform3f  uCameraPos;
    private Uniform1f  uTime;
    private Uniform1f  uIntensity;
    private Uniform1f  uProgress;
    private Uniform1f  uCubeIntensity;

    // smooth in/out so it feels “event global”
    private float master = 0f;

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(INSTANCE);
        PostWorldRenderCallback.EVENT.register(INSTANCE);
    }

    private void ensureInit() {
        if (shader != null) return;

        shader = ShaderEffectManager.getInstance().manage(POST_ID, s -> {
            if (client != null && client.getFramebuffer() instanceof ReadableDepthFramebuffer rdf) {
                s.setSamplerUniform("DepthSampler", rdf.getStillDepthMap());
            }
        });

        uInvTransform   = shader.findUniformMat4("InverseTransformMatrix");
        uCameraPos      = shader.findUniform3f("CameraPosition");
        uTime           = shader.findUniform1f("iTime");
        uIntensity      = shader.findUniform1f("Intensity");
        uProgress       = shader.findUniform1f("Progress");
        uCubeIntensity  = shader.findUniform1f("CubeIntensity");
    }

    @Override
    public void onEndTick(MinecraftClient client) {
        if (client == null || client.world == null) {
            master = Math.max(0f, master - 0.20f);
            return;
        }

        boolean inOverworld = client.world.getRegistryKey() == World.OVERWORLD;
        boolean active = inOverworld && AlienInvasionClientState.active() && !client.isPaused();

        float target = active ? 1f : 0f;

        // fast fade in, smoother fade out
        float up = 0.10f;
        float dn = 0.06f;
        master = (master < target)
                ? Math.min(target, master + up)
                : Math.max(target, master - dn);
    }

    @Override
    public void onWorldRendered(Camera camera, float tickDelta, long limitTime) {
        ensureInit();
        if (shader == null) return;

        if (client == null || client.world == null) return;
        if (client.world.getRegistryKey() != World.OVERWORLD) return;

        if (master <= 0.001f) return;

        // If your server state says not active, still let fade-out happen via master
        float t = (client.world.getTime() + tickDelta) / 20.0f;

        // Use your existing invasion ramps (these can still be 0..1)
        float prog = AlienInvasionClientState.skyProgress01(tickDelta);
        float cubes = AlienInvasionClientState.cubes01(tickDelta);

        uInvTransform.set(GlMatrices.getInverseTransformMatrix(tmpMat));

        Vec3d camPos = camera.getPos();
        uCameraPos.set((float) camPos.x, (float) camPos.y, (float) camPos.z);

        uTime.set(t);
        uIntensity.set(master);
        if (uProgress != null) uProgress.set(MathHelper.clamp(prog, 0f, 1f));
        if (uCubeIntensity != null) uCubeIntensity.set(MathHelper.clamp(cubes, 0f, 1f));

        shader.render(tickDelta);
    }
}