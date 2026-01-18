// src/main/java/net/seep/odd/abilities/zerosuit/client/AnnihilationShaderClient.java
package net.seep.odd.abilities.zerosuit.client;

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
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import net.seep.odd.Oddities;
import net.seep.odd.abilities.power.ZeroSuitPower;

import org.joml.Matrix4f;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public final class AnnihilationShaderClient implements PostWorldRenderCallback, ClientTickEvents.EndTick {
    private AnnihilationShaderClient() {}

    public static final AnnihilationShaderClient INSTANCE = new AnnihilationShaderClient();

    private static final Identifier POST_ID =
            new Identifier(Oddities.MOD_ID, "shaders/post/annihilation.json");

    private final MinecraftClient client = MinecraftClient.getInstance();
    private final Matrix4f tmpMat = new Matrix4f();

    private ManagedShaderEffect shader;
    private UniformMat4 uInvTransform;
    private Uniform3f  uCameraPos;
    private Uniform3f  uBlockPos;
    private Uniform1f  uTime;
    private Uniform1f  uIntensity;

    // Radiation / pillar shared params
    private Uniform1f  uRadius;
    private Uniform1f  uMode;

    // === Pillar state ===
    private Vector3f pillarPos = null;
    private RegistryKey<World> pillarDim = null;
    private int pillarTicks = 0;
    private int pillarDuration = 0;
    private float pillarMaster = 0f;

    // === Radiation state ===
    private Vector3f radPos = null;
    private RegistryKey<World> radDim = null;
    private int radTicks = 0;
    private int radDuration = 0;
    private float radMaxRadius = 0f;
    private float radFade = 0f;
    private boolean radFading = false;

    // Used so the pillar matches the symbol’s final size
    private float lastChargeMaxRadius = 45.0f;

    // === Impact quake state ===
    private Vector3f quakePos = null;
    private RegistryKey<World> quakeDim = null;
    private int quakeTicks = 0;
    private float quakeStrength = 0f;
    private long quakeSeed = 1337L;

    private void ensureInit() {
        if (shader != null) return;

        shader = ShaderEffectManager.getInstance().manage(POST_ID, s -> {
            if (client != null && client.getFramebuffer() instanceof ReadableDepthFramebuffer rdf) {
                s.setSamplerUniform("DepthSampler", rdf.getStillDepthMap());
            }
        });

        uInvTransform = shader.findUniformMat4("InverseTransformMatrix");
        uCameraPos    = shader.findUniform3f("CameraPosition");
        uBlockPos     = shader.findUniform3f("BlockPosition");
        uTime         = shader.findUniform1f("iTime");
        uIntensity    = shader.findUniform1f("Intensity");

        uRadius       = shader.findUniform1f("Radius");
        uMode         = shader.findUniform1f("Mode");
    }

    private boolean pillarActive() {
        if (pillarPos == null) return false;
        if (client == null || client.world == null) return false;
        if (pillarDim == null) return false;
        return client.world.getRegistryKey() == pillarDim && pillarMaster > 0.0001f;
    }

    private boolean radiationActive() {
        if (radPos == null) return false;
        if (client == null || client.world == null) return false;
        if (radDim == null) return false;
        return client.world.getRegistryKey() == radDim && radFade > 0.0001f;
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(INSTANCE);
        PostWorldRenderCallback.EVENT.register(INSTANCE);

        // Apply quake as a camera/world matrix jitter
        WorldRenderEvents.START.register(INSTANCE::applyQuake);

        ClientPlayNetworking.registerGlobalReceiver(ZeroSuitPower.S2C_ZERO_ORBITAL_STRIKE,
                (client, handler, buf, responseSender) -> {
                    double x = buf.readDouble();
                    double y = buf.readDouble();
                    double z = buf.readDouble();
                    int dur = buf.readVarInt();
                    client.execute(() -> INSTANCE.triggerPillar(new Vec3d(x, y, z), dur));
                });

        ClientPlayNetworking.registerGlobalReceiver(ZeroSuitPower.S2C_ZERO_RADIATION_BEGIN,
                (client, handler, buf, responseSender) -> {
                    double x = buf.readDouble();
                    double y = buf.readDouble();
                    double z = buf.readDouble();
                    int dur = buf.readVarInt();
                    float maxR = buf.readFloat();
                    client.execute(() -> INSTANCE.triggerRadiation(new Vec3d(x, y, z), dur, maxR));
                });

        ClientPlayNetworking.registerGlobalReceiver(ZeroSuitPower.S2C_ZERO_RADIATION_CANCEL,
                (client, handler, buf, responseSender) -> {
                    double x = buf.readDouble();
                    double y = buf.readDouble();
                    double z = buf.readDouble();
                    client.execute(() -> INSTANCE.cancelRadiation(new Vec3d(x, y, z)));
                });
    }

    private void triggerPillar(Vec3d pos, int durTicks) {
        ensureInit();
        if (client == null || client.world == null) return;

        // Clear radiation (pillar takes over)
        clearRadiation();

        pillarPos = new Vector3f((float) pos.x, (float) pos.y, (float) pos.z);
        pillarDim = client.world.getRegistryKey();
        pillarTicks = 0;
        pillarDuration = Math.max(1, durTicks);
        pillarMaster = 1.0f;

        // === IMPACT QUAKE ===
        quakePos = new Vector3f((float) pos.x, (float) pos.y, (float) pos.z);
        quakeDim = pillarDim;
        quakeTicks = 26;               // ~1.3s
        quakeStrength = 1.65f;         // strong base (distance falloff will scale it)
        quakeSeed ^= System.nanoTime();
    }

    private void triggerRadiation(Vec3d pos, int durTicks, float maxRadius) {
        ensureInit();
        if (client == null || client.world == null) return;

        radPos = new Vector3f((float) pos.x, (float) pos.y, (float) pos.z);
        radDim = client.world.getRegistryKey();
        radTicks = 0;
        radDuration = Math.max(1, durTicks);
        radMaxRadius = Math.max(0.1f, maxRadius);

        lastChargeMaxRadius = radMaxRadius;

        radFade = 1.0f;
        radFading = false;
    }

    private void cancelRadiation(Vec3d pos) {
        if (radPos == null) return;
        float dx = radPos.x - (float) pos.x;
        float dy = radPos.y - (float) pos.y;
        float dz = radPos.z - (float) pos.z;
        if ((dx*dx + dy*dy + dz*dz) > 0.25f) return;
        radFading = true;
    }

    @Override
    public void onEndTick(MinecraftClient client) {
        if (pillarPos != null) {
            if (client == null || client.world == null || client.isPaused()) {
                pillarMaster = Math.max(0f, pillarMaster - 0.35f);
                if (pillarMaster <= 0.001f) clearPillar();
            } else {
                pillarTicks++;
                if (pillarTicks > pillarDuration + 2) clearPillar();
            }
        }

        if (radPos != null) {
            if (client == null || client.world == null || client.isPaused()) {
                clearRadiation();
            } else {
                radTicks++;
                if (radFading) {
                    radFade = Math.max(0f, radFade - 0.18f);
                    if (radFade <= 0.001f) clearRadiation();
                } else {
                    if (radTicks > radDuration) radFading = true;
                }
            }
        }

        if (quakeTicks > 0) {
            quakeTicks--;
            quakeStrength *= 0.91f;
            if (quakeTicks <= 0 || quakeStrength < 0.02f) {
                quakeTicks = 0;
                quakeStrength = 0f;
                quakePos = null;
                quakeDim = null;
            }
        }
    }

    private void clearPillar() {
        pillarPos = null;
        pillarDim = null;
        pillarTicks = 0;
        pillarDuration = 0;
        pillarMaster = 0f;
    }

    private void clearRadiation() {
        radPos = null;
        radDim = null;
        radTicks = 0;
        radDuration = 0;
        radMaxRadius = 0f;
        radFade = 0f;
        radFading = false;
    }

    private static float clamp01(float v) {
        return MathHelper.clamp(v, 0f, 1f);
    }

    // Simple deterministic noise for shake
    private static float noise(long n) {
        n = (n << 13) ^ n;
        return (1.0f - ((n * (n * n * 15731L + 789221L) + 1376312589L) & 0x7fffffff) / 1073741824.0f);
    }

    // Apply quake to world matrix stack
    private void applyQuake(WorldRenderContext ctx) {
        if (quakeTicks <= 0 || quakePos == null) return;
        if (client == null || client.world == null) return;
        if (quakeDim == null || client.world.getRegistryKey() != quakeDim) return;

        Camera cam = MinecraftClient.getInstance().gameRenderer.getCamera();
        Vec3d camPos = cam.getPos();

        float dx = (float)camPos.x - quakePos.x;
        float dz = (float)camPos.z - quakePos.z;
        float dist = (float)Math.sqrt(dx*dx + dz*dz);

        // Stronger + wider: distance based on (2x the final symbol radius)
        float maxR = Math.max(20f, lastChargeMaxRadius * 2.0f);
        float falloff = 1.0f - clamp01(dist / maxR);
        falloff = falloff * falloff;

        float s = quakeStrength * falloff;
        if (s <= 0.001f) return;

        long tBase = (client.player != null) ? client.player.age : 0L;
        long t = tBase + (long)(ctx.tickDelta() * 10.0f);

        float n1 = noise(quakeSeed + t * 3L) * 0.5f;
        float n2 = noise(quakeSeed ^ (t * 5L)) * 0.5f;

        // BIG shake multiplier
        float amp = s * 0.060f;
        ctx.matrixStack().translate(n1 * amp, n2 * amp, 0.0f);
    }

    @Override
    public void onWorldRendered(Camera worldRenderer, float tickDelta, long limitTime) {
        ensureInit();

        // PILLAR (never cancelled by damage)
        if (pillarActive()) {
            float age = pillarTicks + tickDelta;

            float fadeIn  = clamp01(age / 6.0f);
            float fadeOut = clamp01((pillarDuration - age) / 10.0f);
            float a = clamp01(pillarMaster * Math.min(fadeIn, fadeOut));
            if (a <= 0.001f) return;

            uInvTransform.set(GlMatrices.getInverseTransformMatrix(tmpMat));

            Camera cam = MinecraftClient.getInstance().gameRenderer.getCamera();
            Vec3d camPos = cam.getPos();
            uCameraPos.set((float) camPos.x, (float) camPos.y, (float) camPos.z);

            uBlockPos.set(pillarPos);
            uTime.set(age / 20.0f);
            uIntensity.set(a);

            if (uMode != null) uMode.set(1.0f);

            float r = (lastChargeMaxRadius > 0.1f) ? lastChargeMaxRadius : 45.0f;
            if (uRadius != null) uRadius.set(r);

            shader.render(tickDelta);
            return;
        }

        // RADIATION
        if (radiationActive()) {
            float age = radTicks + tickDelta;

            float fadeIn = clamp01(age / 6.0f);
            float a = clamp01(radFade * fadeIn);
            if (a <= 0.001f) return;

            float pct = (radDuration > 0) ? clamp01(age / (float) radDuration) : 1.0f;
            float r = radMaxRadius * pct;

            uInvTransform.set(GlMatrices.getInverseTransformMatrix(tmpMat));

            Camera cam = MinecraftClient.getInstance().gameRenderer.getCamera();
            Vec3d camPos = cam.getPos();
            uCameraPos.set((float) camPos.x, (float) camPos.y, (float) camPos.z);

            uBlockPos.set(radPos);
            uTime.set(age / 20.0f);
            uIntensity.set(a);

            if (uMode != null) uMode.set(0.0f);
            if (uRadius != null) uRadius.set(r);

            shader.render(tickDelta);
        }
    }
}
