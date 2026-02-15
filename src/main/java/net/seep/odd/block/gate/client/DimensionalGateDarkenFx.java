// FILE: src/main/java/net/seep/odd/block/gate/client/DimensionalGateDarkenFx.java
package net.seep.odd.block.gate.client;

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

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import net.seep.odd.Oddities;
import net.seep.odd.block.gate.DimensionalGateBlock;
import net.seep.odd.block.gate.DimensionalGateBlockEntity;
import net.seep.odd.block.gate.GateStyle;
import net.seep.odd.block.gate.GateStyles;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;

@Environment(EnvType.CLIENT)
public final class DimensionalGateDarkenFx implements PostWorldRenderCallback {
    private DimensionalGateDarkenFx() {}
    public static final DimensionalGateDarkenFx INSTANCE = new DimensionalGateDarkenFx();

    private static final Identifier POST_ID =
            new Identifier(Oddities.MOD_ID, "shaders/post/gate_darken.json");

    // ===== TUNING =====
    private static final int   SCAN_RADIUS = 40;
    private static final int   SCAN_INTERVAL_TICKS = 4;

    private static final float SOURCE_PUSH_IN_BLOCKS = -2.3f;

    // ✅ glow box fits door (you already wanted this)
    private static final float GLOW_PAD_W = 0.00f;
    private static final float GLOW_PAD_H = 0.00f;

    private static final float SPREAD_BLOCKS_PER_SEC = 8.0f;
    private static final float MAX_RADIUS = 32.0f;
    private static final float DARKNESS = 0.85f;

    private static final float PLANE_SCALE = 1.5f;
    private static final float GLOW_STRENGTH = 0.85f;
    // ==================

    private final MinecraftClient client = MinecraftClient.getInstance();
    private final Matrix4f tmpMat = new Matrix4f();

    private ManagedShaderEffect shader;

    private UniformMat4 uInvTransform;
    private Uniform3f  uCameraPos;

    private Uniform3f  uGateCenter;
    private Uniform3f  uGateRight;
    private Uniform3f  uGateUp;
    private Uniform3f  uGateNormal;

    private Uniform1f  uHalfW;
    private Uniform1f  uHalfH;
    private Uniform1f  uPlaneScale;

    private Uniform1f  uGlowStrength;
    private Uniform1f  uGlowHalfW;
    private Uniform1f  uGlowHalfH;

    // ✅ NEW
    private Uniform3f  uGlowColor;

    private Uniform1f  uTime;
    private Uniform1f  uIntensity;

    private Uniform1f  uRadius;
    private Uniform1f  uDarkness;

    private final Long2LongOpenHashMap openStartTickByPos = new Long2LongOpenHashMap();
    private BlockPos activeGateBase = null;
    private long nextScanTick = 0L;

    public static void init() {
        PostWorldRenderCallback.EVENT.register(INSTANCE);
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

        uGateCenter   = shader.findUniform3f("GateCenter");
        uGateRight    = shader.findUniform3f("GateRight");
        uGateUp       = shader.findUniform3f("GateUp");
        uGateNormal   = shader.findUniform3f("GateNormal");

        uHalfW        = shader.findUniform1f("GateHalfWidth");
        uHalfH        = shader.findUniform1f("GateHalfHeight");
        uPlaneScale   = shader.findUniform1f("PlaneScale");

        uGlowStrength = shader.findUniform1f("GlowStrength");
        uGlowHalfW    = shader.findUniform1f("GlowHalfWidth");
        uGlowHalfH    = shader.findUniform1f("GlowHalfHeight");

        // ✅ NEW
        uGlowColor    = shader.findUniform3f("GlowColor");

        uTime         = shader.findUniform1f("iTime");
        uIntensity    = shader.findUniform1f("Intensity");

        uRadius       = shader.findUniform1f("Radius");
        uDarkness     = shader.findUniform1f("Darkness");
    }

    @Override
    public void onWorldRendered(Camera camera, float tickDelta, long limitTime) {
        ensureInit();
        if (client == null || client.world == null || client.player == null) return;

        ClientWorld world = client.world;
        long now = world.getTime();

        if (now >= nextScanTick) {
            nextScanTick = now + SCAN_INTERVAL_TICKS;
            activeGateBase = findNearestOpenGate(world);
        }

        if (activeGateBase == null) return;

        BlockState baseState = world.getBlockState(activeGateBase);
        if (!(baseState.getBlock() instanceof DimensionalGateBlock)) return;

        if (!baseState.get(DimensionalGateBlock.OPEN)) {
            openStartTickByPos.remove(activeGateBase.asLong());
            return;
        }

        long key = activeGateBase.asLong();
        if (!openStartTickByPos.containsKey(key)) {
            openStartTickByPos.put(key, now);
        }
        long start = openStartTickByPos.get(key);
        float ageTicks = (now - start) + tickDelta;

        float radius = Math.min(MAX_RADIUS, (ageTicks / 20.0f) * SPREAD_BLOCKS_PER_SEC);
        if (radius <= 0.25f) return;

        float fadeIn = MathHelper.clamp(ageTicks / 8.0f, 0f, 1f);

        Direction facing = baseState.get(DimensionalGateBlock.FACING);
        Direction rightD = facing.rotateYClockwise();

        double cx = activeGateBase.getX() + 0.5 + rightD.getOffsetX() * ((DimensionalGateBlock.WIDTH - 1) / 2.0);
        double cy = activeGateBase.getY() + ((DimensionalGateBlock.HEIGHT - 1) / 2.0) + 0.5;
        double cz = activeGateBase.getZ() + 0.5 + rightD.getOffsetZ() * ((DimensionalGateBlock.WIDTH - 1) / 2.0);

        Vec3d gateCenter = new Vec3d(cx, cy, cz)
                .add(facing.getOffsetX() * SOURCE_PUSH_IN_BLOCKS, 0.0, facing.getOffsetZ() * SOURCE_PUSH_IN_BLOCKS);

        Vec3d gateRight  = new Vec3d(rightD.getOffsetX(), 0.0, rightD.getOffsetZ());
        Vec3d gateUp     = new Vec3d(0.0, 1.0, 0.0);
        Vec3d gateNormal = new Vec3d(facing.getOffsetX(), 0.0, facing.getOffsetZ());

        float halfW = (float) (DimensionalGateBlock.WIDTH / 2.0);
        float halfH = (float) (DimensionalGateBlock.HEIGHT / 2.0);

        Vec3d camPos = camera.getPos();
        double d2 = camPos.squaredDistanceTo(gateCenter);
        float distFade = 1.0f - (float) MathHelper.clamp(d2 / (96.0 * 96.0), 0.0, 1.0);

        float intensity = fadeIn * distFade;
        if (intensity <= 0.001f) return;

        // ✅ Resolve style -> glow color
        Vector3f glow = new Vector3f(0.10f, 0.90f, 0.25f); // fallback (rotten-ish)
        BlockEntity be = world.getBlockEntity(activeGateBase);
        if (be instanceof DimensionalGateBlockEntity gateBe) {
            Identifier styleId = gateBe.getStyleId();
            GateStyle style = GateStyles.get(styleId);
            if (style != null && style.glowColor() != null) glow = style.glowColor();
        }

        // uniforms
        uInvTransform.set(GlMatrices.getInverseTransformMatrix(tmpMat));
        uCameraPos.set((float) camPos.x, (float) camPos.y, (float) camPos.z);

        uGateCenter.set((float) gateCenter.x, (float) gateCenter.y, (float) gateCenter.z);
        uGateRight.set((float) gateRight.x, (float) gateRight.y, (float) gateRight.z);
        uGateUp.set((float) gateUp.x, (float) gateUp.y, (float) gateUp.z);
        uGateNormal.set((float) gateNormal.x, (float) gateNormal.y, (float) gateNormal.z);

        uHalfW.set(halfW);
        uHalfH.set(halfH);

        uPlaneScale.set(PLANE_SCALE);
        uGlowStrength.set(GLOW_STRENGTH);

        if (uGlowHalfW != null) uGlowHalfW.set(halfW + GLOW_PAD_W);
        if (uGlowHalfH != null) uGlowHalfH.set(halfH + GLOW_PAD_H);

        // ✅ NEW
        if (uGlowColor != null) uGlowColor.set(glow.x, glow.y, glow.z);

        float t = ((client.player != null ? client.player.age : 0) + tickDelta) / 20.0f;
        uTime.set(t);
        uIntensity.set(intensity);

        if (uRadius != null) uRadius.set(radius);
        if (uDarkness != null) uDarkness.set(DARKNESS);

        shader.render(tickDelta);
    }

    private BlockPos findNearestOpenGate(ClientWorld world) {
        BlockPos center = client.player.getBlockPos();
        int r = SCAN_RADIUS;

        int yMin = center.getY() - 4;
        int yMax = center.getY() + 8;

        BlockPos bestBase = null;
        double bestD2 = Double.MAX_VALUE;

        BlockPos.Mutable m = new BlockPos.Mutable();

        for (int y = yMin; y <= yMax; y++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    m.set(center.getX() + dx, y, center.getZ() + dz);

                    BlockState st = world.getBlockState(m);
                    if (!(st.getBlock() instanceof DimensionalGateBlock)) continue;

                    BlockPos base = DimensionalGateBlock.getBasePos(m, st);
                    BlockState bs = world.getBlockState(base);
                    if (!(bs.getBlock() instanceof DimensionalGateBlock)) continue;
                    if (!bs.get(DimensionalGateBlock.OPEN)) continue;

                    double d2 = base.getSquaredDistance(center);
                    if (d2 < bestD2) {
                        bestD2 = d2;
                        bestBase = base.toImmutable();
                    }
                }
            }
        }

        return bestBase;
    }
}
