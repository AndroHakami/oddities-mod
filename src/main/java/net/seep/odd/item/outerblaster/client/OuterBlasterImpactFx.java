
package net.seep.odd.item.outerblaster.client;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
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
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.seep.odd.Oddities;
import net.seep.odd.item.outerblaster.OuterBlasterFxNet;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public final class OuterBlasterImpactFx implements PostWorldRenderCallback {
    private static final Identifier POST_ID = new Identifier(Oddities.MOD_ID, "shaders/post/outer_blaster_impact.json");

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

    private record Impact(double x, double y, double z, float radius, long endTick, int totalTicks) {}
    private static final Long2ObjectOpenHashMap<Impact> IMPACTS = new Long2ObjectOpenHashMap<>();

    private OuterBlasterImpactFx() {}

    public static void init() {
        if (inited) return;
        inited = true;

        PostWorldRenderCallback.EVENT.register(new OuterBlasterImpactFx());

        ClientPlayNetworking.registerGlobalReceiver(OuterBlasterFxNet.IMPACT_S2C, (client, handler, buf, sender) -> {
            long id = buf.readLong();
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            float radius = buf.readFloat();
            int duration = buf.readInt();

            client.execute(() -> upsert(id, x, y, z, radius, duration));
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client == null || client.world == null) return;
            long now = client.world.getTime();

            var it = IMPACTS.long2ObjectEntrySet().fastIterator();
            while (it.hasNext()) {
                var e = it.next();
                if (now >= e.getValue().endTick) it.remove();
            }
        });
    }

    private static void upsert(long id, double x, double y, double z, float radius, int duration) {
        if (CLIENT == null || CLIENT.world == null) return;
        int total = Math.max(1, duration);
        IMPACTS.put(id, new Impact(x, y, z, radius, CLIENT.world.getTime() + total, total));
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
        if (CLIENT == null || CLIENT.world == null || IMPACTS.isEmpty()) return;

        ensureInit();
        if (shader == null) return;

        ClientWorld world = CLIENT.world;
        long now = world.getTime();

        var camPos = camera.getPos();
        uInvTransform.set(GlMatrices.getInverseTransformMatrix(TMP));
        uCameraPos.set((float) camPos.x, (float) camPos.y, (float) camPos.z);
        uTime.set(((CLIENT.player != null ? CLIENT.player.age : 0) + tickDelta) / 20.0f);

        var it = IMPACTS.long2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            var e = it.next();
            Impact impact = e.getValue();

            float remaining = (float) ((impact.endTick - now) - tickDelta);
            if (remaining <= 0.0f) {
                it.remove();
                continue;
            }

            float rem01 = MathHelper.clamp(remaining / (float) impact.totalTicks, 0.0f, 1.0f);
            float age01 = 1.0f - rem01;

            float fadeIn = smoothstep(0.00f, 0.12f, age01);
            float fadeOut = smoothstep(0.00f, 0.40f, rem01);
            float intensity = fadeIn * fadeOut * (1.0f + 0.45f * (1.0f - age01));

            uCenter.set((float) impact.x, (float) impact.y, (float) impact.z);
            uRadius.set(impact.radius);
            uAge01.set(age01);
            uIntensity.set(intensity);

            shader.render(tickDelta);
        }
    }

    private static float smoothstep(float a, float b, float x) {
        float t = MathHelper.clamp((x - a) / (b - a), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }
}
