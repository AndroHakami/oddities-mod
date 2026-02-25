// FILE: src/main/java/net/seep/odd/abilities/artificer/mixer/brew/client/AutoColdFx.java
package net.seep.odd.abilities.artificer.mixer.brew.client;

import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
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
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import net.seep.odd.abilities.artificer.mixer.AutoColdNet;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public final class AutoColdFx implements PostWorldRenderCallback {
    private AutoColdFx() {}

    private static final Identifier POST_ID = new Identifier("odd", "shaders/post/auto_cold.json");

    private static boolean inited = false;
    private static final MinecraftClient client = MinecraftClient.getInstance();
    private static final Matrix4f tmpMat = new Matrix4f();

    private static ManagedShaderEffect shader;

    private static UniformMat4 uInv;
    private static Uniform3f uCam;

    private static Uniform3f uCenter;
    private static Uniform1f uRadius;
    // keep height uniform for compat (shader can ignore it)
    private static Uniform1f uHeight;

    private static Uniform1f uTime;
    private static Uniform1f uIntensity;

    // entityId -> endWorldTime (client world time)
    private static final Int2LongOpenHashMap ACTIVE = new Int2LongOpenHashMap();

    // perf limits (post shaders are expensive if spammed)
    private static final int MAX_RENDERS_PER_FRAME = 4;
    private static final float MAX_DIST = 64.0f; // blocks

    public static void init() {
        if (inited) return;
        inited = true;

        PostWorldRenderCallback.EVENT.register(new AutoColdFx());

        ClientPlayNetworking.registerGlobalReceiver(AutoColdNet.AUTO_COLD_S2C, (mc, handler, buf, rs) -> {
            int entityId = buf.readInt();
            boolean active = buf.readBoolean();
            int dur = buf.readVarInt();

            mc.execute(() -> {
                if (mc.world == null) return;
                if (active) ACTIVE.put(entityId, mc.world.getTime() + dur);
                else ACTIVE.remove(entityId);
            });
        });

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc == null || mc.world == null) return;
            long now = mc.world.getTime();

            var it = ACTIVE.int2LongEntrySet().fastIterator();
            while (it.hasNext()) {
                var e = it.next();
                if (now >= e.getLongValue()) it.remove();
            }
        });
    }

    private static void ensureInit() {
        if (shader != null) return;

        shader = ShaderEffectManager.getInstance().manage(POST_ID, s -> {
            if (client != null && client.getFramebuffer() instanceof ReadableDepthFramebuffer rdf) {
                s.setSamplerUniform("DepthSampler", rdf.getStillDepthMap());
            }
        });

        uInv = shader.findUniformMat4("InverseTransformMatrix");
        uCam = shader.findUniform3f("CameraPosition");

        uCenter = shader.findUniform3f("Center");
        uRadius = shader.findUniform1f("Radius");
        uHeight = shader.findUniform1f("Height"); // optional in shader

        uTime = shader.findUniform1f("iTime");
        uIntensity = shader.findUniform1f("Intensity");
    }

    @Override
    public void onWorldRendered(Camera camera, float tickDelta, long limitTime) {
        if (client == null || client.world == null || ACTIVE.isEmpty()) return;

        ensureInit();
        if (shader == null) return;

        ClientWorld world = client.world;
        long now = world.getTime();

        Vec3d camPos = camera.getPos();

        uInv.set(GlMatrices.getInverseTransformMatrix(tmpMat));
        uCam.set((float) camPos.x, (float) camPos.y, (float) camPos.z);

        float time = ((client.player != null ? client.player.age : 0) + tickDelta) / 20.0f;
        uTime.set(time);

        int renders = 0;

        var it = ACTIVE.int2LongEntrySet().fastIterator();
        while (it.hasNext()) {
            var e = it.next();
            int id = e.getIntKey();
            long end = e.getLongValue();
            if (now >= end) { it.remove(); continue; }

            Entity ent = world.getEntityById(id);
            if (ent == null) continue;

            // distance cull
            double dx = ent.getX() - camPos.x;
            double dy = ent.getY() - camPos.y;
            double dz = ent.getZ() - camPos.z;
            if ((dx*dx + dy*dy + dz*dz) > (MAX_DIST * MAX_DIST)) continue;

            // perf limit
            if (renders >= MAX_RENDERS_PER_FRAME) break;

            // bubble centered around chest-ish (looks nicer than feet)
            float cy = (float)(ent.getY() + ent.getHeight() * 0.55);

            uCenter.set((float)ent.getX(), cy, (float)ent.getZ());

            // bubble size
            uRadius.set(2.55f);

            // keep for json compatibility (bubble shader ignores it)
            if (uHeight != null) uHeight.set(0.0f);

            // fade nicely near the end (last ~1.2s)
            float leftTicks = (float)((end - now) - tickDelta);
            float fade = MathHelper.clamp(leftTicks / (20.0f * 1.2f), 0.0f, 1.0f);

            float intensity = 0.35f + 0.65f * fade; // never fully disappears abruptly
            uIntensity.set(intensity);

            shader.render(tickDelta);
            renders++;
        }
    }
}
