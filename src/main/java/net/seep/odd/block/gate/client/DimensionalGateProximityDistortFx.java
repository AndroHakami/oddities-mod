// FILE: src/main/java/net/seep/odd/block/gate/client/DimensionalGateProximityDistortFx.java
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

import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public final class DimensionalGateProximityDistortFx implements PostWorldRenderCallback {
    private DimensionalGateProximityDistortFx() {}
    public static final DimensionalGateProximityDistortFx INSTANCE = new DimensionalGateProximityDistortFx();

    private static final Identifier POST_ID =
            new Identifier(Oddities.MOD_ID, "shaders/post/gate_distort.json");

    // ===== TUNING =====
    private static final int   SCAN_RADIUS = 44;           // blocks
    private static final int   SCAN_INTERVAL_TICKS = 6;    // how often to scan for gates
    private static final float EFFECT_FAR = 6.0f;         // start showing at this distance
    private static final float EFFECT_NEAR = 3.5f;         // max effect at this distance (or closer)
    private static final float MAX_INTENSITY = 1.0f;       // global cap (0..1)
    // ==================

    private final MinecraftClient client = MinecraftClient.getInstance();
    private final Matrix4f tmpMat = new Matrix4f();

    private ManagedShaderEffect shader;

    private UniformMat4 uInvTransform;
    private Uniform3f  uCameraPos;
    private Uniform3f  uGatePos;

    private Uniform1f  uTime;
    private Uniform1f  uIntensity;
    private Uniform1f  uProximity;

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
        uGatePos      = shader.findUniform3f("GatePosition");

        uTime         = shader.findUniform1f("iTime");
        uIntensity    = shader.findUniform1f("Intensity");
        uProximity    = shader.findUniform1f("Proximity");
    }

    @Override
    public void onWorldRendered(Camera camera, float tickDelta, long limitTime) {
        ensureInit();
        if (client == null || client.world == null || client.player == null) return;

        ClientWorld world = client.world;
        long now = world.getTime();

        // periodically locate nearest open gate (so it works even if you aren't looking at it)
        if (now >= nextScanTick) {
            nextScanTick = now + SCAN_INTERVAL_TICKS;
            activeGateBase = findNearestOpenGate(world);
        }

        if (activeGateBase == null) return;

        BlockState baseState = world.getBlockState(activeGateBase);
        if (!(baseState.getBlock() instanceof DimensionalGateBlock)) return;
        if (!baseState.get(DimensionalGateBlock.OPEN)) return;

        Direction facing = baseState.get(DimensionalGateBlock.FACING);
        Direction rightD = facing.rotateYClockwise();

        // center of gate plane
        double cx = activeGateBase.getX() + 0.5 + rightD.getOffsetX() * ((DimensionalGateBlock.WIDTH - 1) / 2.0);
        double cy = activeGateBase.getY() + ((DimensionalGateBlock.HEIGHT - 1) / 2.0) + 0.5;
        double cz = activeGateBase.getZ() + 0.5 + rightD.getOffsetZ() * ((DimensionalGateBlock.WIDTH - 1) / 2.0);

        Vec3d gatePos = new Vec3d(cx, cy, cz);

        // distance from player to gate
        Vec3d pPos = client.player.getPos();
        double dist = pPos.distanceTo(gatePos);

        // proximity 0..1 (smooth)
        float prox = 1.0f - (float)((dist - EFFECT_NEAR) / Math.max(0.0001, (EFFECT_FAR - EFFECT_NEAR)));
        prox = MathHelper.clamp(prox, 0.0f, 1.0f);
        // nicer curve
        prox = prox * prox * (3.0f - 2.0f * prox);

        float intensity = MathHelper.clamp(prox * MAX_INTENSITY, 0f, 1f);
        if (intensity <= 0.001f) return;

        Vec3d camPos = camera.getPos();

        uInvTransform.set(GlMatrices.getInverseTransformMatrix(tmpMat));
        uCameraPos.set((float) camPos.x, (float) camPos.y, (float) camPos.z);
        uGatePos.set((float) gatePos.x, (float) gatePos.y, (float) gatePos.z);

        float t = ((client.player != null ? client.player.age : 0) + tickDelta) / 20.0f;
        uTime.set(t);
        uIntensity.set(intensity);
        if (uProximity != null) uProximity.set(prox);

        shader.render(tickDelta);
    }

    private BlockPos findNearestOpenGate(ClientWorld world) {
        BlockPos center = client.player.getBlockPos();
        int r = SCAN_RADIUS;

        int yMin = center.getY() - 5;
        int yMax = center.getY() + 10;

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
