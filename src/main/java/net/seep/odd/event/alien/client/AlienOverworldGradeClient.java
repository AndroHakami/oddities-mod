package net.seep.odd.event.alien.client;

import ladysnake.satin.api.event.PostWorldRenderCallback;
import ladysnake.satin.api.experimental.ReadableDepthFramebuffer;
import ladysnake.satin.api.managed.ManagedShaderEffect;
import ladysnake.satin.api.managed.ShaderEffectManager;
import ladysnake.satin.api.managed.uniform.Uniform1f;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import net.seep.odd.Oddities;

@Environment(EnvType.CLIENT)
public final class AlienOverworldGradeClient implements PostWorldRenderCallback, ClientTickEvents.EndTick {

    public static final AlienOverworldGradeClient INSTANCE = new AlienOverworldGradeClient();

    private static final Identifier POST_ID =
            new Identifier(Oddities.MOD_ID, "shaders/post/alien_overworld_grade.json");

    private final MinecraftClient client = MinecraftClient.getInstance();

    private ManagedShaderEffect shader;
    private Uniform1f uTime;
    private Uniform1f uIntensity;
    private Uniform1f uProgress;

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

        uTime      = shader.findUniform1f("iTime");
        uIntensity = shader.findUniform1f("Intensity");
        uProgress  = shader.findUniform1f("Progress");
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

        // smooth fade in/out
        float up = 0.08f;
        float dn = 0.06f;
        master = (master < target)
                ? Math.min(target, master + up)
                : Math.max(target, master - dn);
    }

    @Override
    public void onWorldRendered(Camera camera, float tickDelta, long limitTime) {
        ensureInit();
        if (shader == null || client == null || client.world == null) return;
        if (client.world.getRegistryKey() != World.OVERWORLD) return;
        if (master <= 0.001f) return;

        float t = (client.world.getTime() + tickDelta) / 20.0f;

        // Use your invasion’s ramp so it “comes in” with the sky break
        float prog = AlienInvasionClientState.skyProgress01(tickDelta);

        uTime.set(t);
        uIntensity.set(master);
        if (uProgress != null) uProgress.set(MathHelper.clamp(prog, 0f, 1f));

        shader.render(tickDelta);
    }
}