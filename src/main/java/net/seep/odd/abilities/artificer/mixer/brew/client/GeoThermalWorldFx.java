package net.seep.odd.abilities.artificer.mixer.brew.client;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
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
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;

import java.util.UUID;

@Environment(EnvType.CLIENT)
public final class GeoThermalWorldFx implements PostWorldRenderCallback {
    private GeoThermalWorldFx() {}

    public static final GeoThermalWorldFx INSTANCE = new GeoThermalWorldFx();

    private static final Identifier POST_ID = new Identifier("odd", "shaders/post/geothermal_world.json");

    // tuning (this is now “model radius”, not ground ring)
    private static final float AURA_RADIUS = 0.70f;      // ~player body envelope
    private static final float MAX_VIEW_DIST = 56.0f;

    // perf cap: max auras rendered per frame
    private static final int MAX_AURAS_PER_FRAME = 6;

    private static boolean inited = false;

    private final MinecraftClient client = MinecraftClient.getInstance();
    private final Matrix4f tmpMat = new Matrix4f();

    private ManagedShaderEffect shader;

    private UniformMat4 uInvTransform;
    private Uniform3f  uCameraPos;

    private Uniform3f  uPlayerPos;
    private Uniform1f  uRadius;

    private Uniform1f  uTime;
    private Uniform1f  uIntensity;

    private Uniform3f  uColor;

    // ticks remaining per player UUID (self + others)
    private static final Object2IntOpenHashMap<UUID> TICKS_LEFT = new Object2IntOpenHashMap<>();
    static { TICKS_LEFT.defaultReturnValue(0); }

    private static float t = 0f;

    public static void init() {
        if (inited) return;
        inited = true;
        PostWorldRenderCallback.EVENT.register(INSTANCE);
    }

    public static void setActive(UUID id, int ticks) {
        int prev = TICKS_LEFT.getInt(id);
        if (ticks > prev) TICKS_LEFT.put(id, ticks);
    }

    public static void clear(UUID id) {
        TICKS_LEFT.removeInt(id);
    }

    public static int getTicksLeft(UUID id) {
        return TICKS_LEFT.getInt(id);
    }

    public static void tickClient() {
        if (TICKS_LEFT.isEmpty()) return;

        var it = TICKS_LEFT.object2IntEntrySet().fastIterator();
        while (it.hasNext()) {
            var e = it.next();
            int v = e.getIntValue() - 1;
            if (v <= 0) it.remove();
            else e.setValue(v);
        }
    }

    private void ensureInit() {
        if (shader != null) return;

        shader = ShaderEffectManager.getInstance().manage(POST_ID, s -> {
            if (client != null && client.getFramebuffer() instanceof ReadableDepthFramebuffer rdf) {
                s.setSamplerUniform("DepthSampler", rdf.getStillDepthMap());
            }
        });

        uInvTransform = shader.findUniformMat4("InverseTransformMatrix");
        uCameraPos    = shader.findUniform3f("CameraPosition");

        uPlayerPos    = shader.findUniform3f("PlayerPos");
        uRadius       = shader.findUniform1f("Radius");

        uTime         = shader.findUniform1f("iTime");
        uIntensity    = shader.findUniform1f("Intensity");

        uColor        = shader.findUniform3f("Color");
    }

    @Override
    public void onWorldRendered(Camera camera, float tickDelta, long limitTime) {
        if (client == null || client.world == null) return;
        if (TICKS_LEFT.isEmpty()) return;

        ensureInit();
        if (shader == null) return;

        ClientWorld world = client.world;
        var camPos = camera.getPos();

        // advance time once per frame
        t += 0.020f;

        uInvTransform.set(GlMatrices.getInverseTransformMatrix(tmpMat));
        uCameraPos.set((float) camPos.x, (float) camPos.y, (float) camPos.z);

        uRadius.set(AURA_RADIUS);
        uTime.set(t);

        // hot orange
        uColor.set(1.0f, 0.45f, 0.08f);

        int rendered = 0;

        for (PlayerEntity p : world.getPlayers()) {
            int ticks = TICKS_LEFT.getInt(p.getUuid());
            if (ticks <= 0) continue;

            double d2 = camPos.squaredDistanceTo(p.getX(), p.getY(), p.getZ());
            if (d2 > (MAX_VIEW_DIST * MAX_VIEW_DIST)) continue;

            // fade w/ time + distance
            float timeFade = MathHelper.clamp(ticks / (20f * 3f), 0.0f, 1.0f); // last ~3s fades down nicely
            float distFade = 1.0f - (float) MathHelper.clamp(d2 / (MAX_VIEW_DIST * MAX_VIEW_DIST), 0.0, 1.0);
            float intensity = timeFade * distFade;

            if (intensity <= 0.001f) continue;

            // ✅ torso/center (entity pos is feet)
            float cx = (float) p.getX();
            float cy = (float) (p.getY() + 0.90); // torso center
            float cz = (float) p.getZ();

            uPlayerPos.set(cx, cy, cz);
            uIntensity.set(intensity);

            shader.render(tickDelta);

            rendered++;
            if (rendered >= MAX_AURAS_PER_FRAME) break;
        }
    }
}
