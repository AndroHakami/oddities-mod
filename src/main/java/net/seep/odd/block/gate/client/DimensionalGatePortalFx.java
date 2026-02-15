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
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import net.seep.odd.Oddities;
import net.seep.odd.block.gate.DimensionalGateBlock;
import net.seep.odd.block.gate.DimensionalGateBlockEntity;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public final class DimensionalGatePortalFx implements PostWorldRenderCallback {
    private DimensionalGatePortalFx() {}

    public static final DimensionalGatePortalFx INSTANCE = new DimensionalGatePortalFx();

    // Default (rotten roots) shader chain
    private static final Identifier POST_DEFAULT_ID =
            new Identifier(Oddities.MOD_ID, "shaders/post/gate_portal.json");

    // ====================
    // TUNING
    // ====================
    private static final double RAYCAST_DIST = 96.0;

    /**
     * MUST MATCH your BE’s VISUAL_PUSH so the shader plane lines up with teleport volume.
     * (Your BE uses +0.55 along FACING.)
     */
    private static final double VISUAL_PUSH = -2.25;

    /**
     * Extra offset applied on top of VISUAL_PUSH.
     * Negative = toward player, Positive = deeper “into” gate direction.
     */
    private static final double EXTRA_PLANE_PUSH = 0.0;

    /**
     * Canvas scale (what you asked for: "more space" than the 4x5 door).
     * 1.0 = exact door size. 1.5 = noticeably larger.
     *
     * Tip: If you want "higher" more than "wider", increase CANVAS_SCALE_H only.
     */
    private static final double CANVAS_SCALE_W = 1.50;
    private static final double CANVAS_SCALE_H = 1.70;

    // Auto-detect nearest open gate within this radius (blocks)
    private static final int SCAN_RADIUS = 32;

    // Scan every N ticks (lower = more responsive, higher = cheaper)
    private static final int SCAN_INTERVAL_TICKS = 5;

    // When you DO look at a gate, keep rendering that gate for this long (ticks)
    private static final int LOCK_TICKS_AFTER_LOOK = 80;
    // ====================

    private final MinecraftClient client = MinecraftClient.getInstance();
    private final Matrix4f tmpMat = new Matrix4f();

    private Identifier activePostId = null;
    private ManagedShaderEffect shader;

    private UniformMat4 uInvTransform;
    private Uniform3f  uCameraPos;

    private Uniform3f  uGateCenter;
    private Uniform3f  uGateRight;
    private Uniform3f  uGateUp;
    private Uniform3f  uGateNormal;

    private Uniform1f  uHalfW;
    private Uniform1f  uHalfH;

    private Uniform1f  uTime;
    private Uniform1f  uIntensity;

    // Cached/locked gate so it stays visible even if you aren't aiming at it
    private BlockPos lockedBasePos = null;
    private long lockUntilTick = 0L;
    private long nextScanTick = 0L;

    public static void init() {
        PostWorldRenderCallback.EVENT.register(INSTANCE);
    }

    private void ensureInit(Identifier postId) {
        if (shader != null && postId.equals(activePostId)) return;

        // (Re)load shader for this style
        activePostId = postId;
        shader = ShaderEffectManager.getInstance().manage(activePostId, s -> {
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

        uTime         = shader.findUniform1f("iTime");
        uIntensity    = shader.findUniform1f("Intensity");
    }

    private record GatePick(BlockPos basePos, BlockState baseState, Direction facing, @Nullable Identifier styleId) {}

    @Override
    public void onWorldRendered(Camera camera, float tickDelta, long limitTime) {
        if (client == null || client.world == null || client.player == null) return;

        ClientWorld world = client.world;
        long now = world.getTime();

        // 1) Try raycast lock-on first (best accuracy)
        GatePick pick = pickFromRaycast(world, tickDelta);
        if (pick != null) {
            lockedBasePos = pick.basePos;
            lockUntilTick = now + LOCK_TICKS_AFTER_LOOK;
        } else {
            // 2) If we have a locked gate still valid, use it
            pick = pickFromLock(world, now);
            if (pick == null) {
                // 3) Otherwise periodically scan for nearest open gate
                if (now >= nextScanTick) {
                    nextScanTick = now + SCAN_INTERVAL_TICKS;
                    pick = findNearestOpenGate(world);
                    if (pick != null) {
                        lockedBasePos = pick.basePos;
                        lockUntilTick = now + LOCK_TICKS_AFTER_LOOK;
                    }
                } else if (lockedBasePos != null) {
                    pick = pickFromLock(world, now);
                }
            }
        }

        if (pick == null) return;

        BlockPos basePos = pick.basePos;
        BlockState baseState = pick.baseState;

        // Only render when OPEN (so closed gate shows nothing)
        if (!baseState.get(DimensionalGateBlock.OPEN)) return;

        // Decide which post chain to use based on BE style (fallback to default)
        Identifier postId = resolvePostIdForGate(world, basePos, pick.styleId);
        ensureInit(postId);
        if (shader == null) return;

        Direction facing = pick.facing;
        Direction rightD = facing.rotateYClockwise();

        // Center of doorway plane (controller is bottom-left in multiblock)
        double cx = basePos.getX() + 0.5 + rightD.getOffsetX() * ((DimensionalGateBlock.WIDTH - 1) / 2.0);
        double cy = basePos.getY() + ((DimensionalGateBlock.HEIGHT - 1) / 2.0) + 0.5;
        double cz = basePos.getZ() + 0.5 + rightD.getOffsetZ() * ((DimensionalGateBlock.WIDTH - 1) / 2.0);

        Vec3d gateNormal = new Vec3d(facing.getOffsetX(), 0.0, facing.getOffsetZ()).normalize();

        // ✅ Visual plane position: MATCH BE convention (positive along FACING)
        Vec3d gateCenter = new Vec3d(cx, cy, cz)
                .add(gateNormal.multiply(VISUAL_PUSH + EXTRA_PLANE_PUSH));

        // Basis vectors
        Vec3d gateRight  = new Vec3d(rightD.getOffsetX(), 0.0, rightD.getOffsetZ()).normalize();
        Vec3d gateUp     = new Vec3d(0.0, 1.0, 0.0);

        // Canvas half sizes (scaled bigger than the door)
        float halfW = (float) ((DimensionalGateBlock.WIDTH / 2.0) * CANVAS_SCALE_W);
        float halfH = (float) ((DimensionalGateBlock.HEIGHT / 2.0) * CANVAS_SCALE_H);

        // Distance fade so it doesn’t pop from far away
        Vec3d camPos = camera.getPos();
        double d2 = camPos.squaredDistanceTo(gateCenter);
        float distFade = 1.0f - (float) MathHelper.clamp(d2 / (RAYCAST_DIST * RAYCAST_DIST), 0.0, 1.0);

        float intensity = distFade;
        if (intensity <= 0.001f) return;

        // Set uniforms + render
        uInvTransform.set(GlMatrices.getInverseTransformMatrix(tmpMat));
        uCameraPos.set((float) camPos.x, (float) camPos.y, (float) camPos.z);

        uGateCenter.set((float) gateCenter.x, (float) gateCenter.y, (float) gateCenter.z);
        uGateRight.set((float) gateRight.x, (float) gateRight.y, (float) gateRight.z);
        uGateUp.set((float) gateUp.x, (float) gateUp.y, (float) gateUp.z);
        uGateNormal.set((float) gateNormal.x, (float) gateNormal.y, (float) gateNormal.z);

        uHalfW.set(halfW);
        uHalfH.set(halfH);

        float t = ((client.player != null ? client.player.age : 0) + tickDelta) / 20.0f;
        uTime.set(t);
        uIntensity.set(intensity);

        shader.render(tickDelta);
    }

    /**
     * If you add new gate styles, drop a matching post chain:
     *  - styleId: odd:atheneum_gate
     *  - file:    odd:shaders/post/gate_portal_atheneum.json
     *
     * If missing, it falls back to gate_portal.json.
     */
    private Identifier resolvePostIdForGate(ClientWorld world, BlockPos basePos, @Nullable Identifier styleIdFromPick) {
        Identifier styleId = styleIdFromPick;

        // Prefer the BE style id (authoritative) if present
        BlockEntity be = world.getBlockEntity(basePos);
        if (be instanceof DimensionalGateBlockEntity gateBe) {
            styleId = gateBe.getStyleId();
        }

        if (styleId == null) return POST_DEFAULT_ID;

        // rotten roots default
        if (styleId.getNamespace().equals(Oddities.MOD_ID) && styleId.getPath().equals("rotten_roots_gate")) {
            return POST_DEFAULT_ID;
        }

        // heuristic: "<theme>_gate" -> "gate_portal_<theme>.json"
        String theme = styleId.getPath();
        if (theme.endsWith("_gate")) theme = theme.substring(0, theme.length() - "_gate".length());

        return new Identifier(Oddities.MOD_ID, "shaders/post/gate_portal_" + theme + ".json");
    }

    private GatePick pickFromRaycast(ClientWorld world, float tickDelta) {
        Entity camEnt = client.getCameraEntity();
        if (camEnt == null) return null;

        HitResult hr = camEnt.raycast(RAYCAST_DIST, tickDelta, false);
        if (hr.getType() != HitResult.Type.BLOCK) return null;

        BlockPos hitPos = ((BlockHitResult) hr).getBlockPos();
        BlockState hitState = world.getBlockState(hitPos);
        if (!(hitState.getBlock() instanceof DimensionalGateBlock)) return null;

        BlockPos basePos = DimensionalGateBlock.getBasePos(hitPos, hitState);
        BlockState baseState = world.getBlockState(basePos);
        if (!(baseState.getBlock() instanceof DimensionalGateBlock)) return null;

        Direction facing = baseState.get(DimensionalGateBlock.FACING);

        // read style from BE if present (nice for immediate switching)
        Identifier styleId = null;
        BlockEntity be = world.getBlockEntity(basePos);
        if (be instanceof DimensionalGateBlockEntity gateBe) styleId = gateBe.getStyleId();

        return new GatePick(basePos, baseState, facing, styleId);
    }

    private GatePick pickFromLock(ClientWorld world, long now) {
        if (lockedBasePos == null) return null;
        if (now > lockUntilTick) return null;

        BlockState baseState = world.getBlockState(lockedBasePos);
        if (!(baseState.getBlock() instanceof DimensionalGateBlock)) return null;

        Direction facing = baseState.get(DimensionalGateBlock.FACING);

        Identifier styleId = null;
        BlockEntity be = world.getBlockEntity(lockedBasePos);
        if (be instanceof DimensionalGateBlockEntity gateBe) styleId = gateBe.getStyleId();

        return new GatePick(lockedBasePos, baseState, facing, styleId);
    }

    private GatePick findNearestOpenGate(ClientWorld world) {
        if (client.player == null) return null;

        BlockPos center = client.player.getBlockPos();
        int r = SCAN_RADIUS;

        int yMin = center.getY() - 2;
        int yMax = center.getY() + 5;

        BlockPos bestBase = null;
        BlockState bestState = null;
        Direction bestFacing = null;
        Identifier bestStyle = null;
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
                    if (!bs.get(DimensionalGateBlock.OPEN)) continue; // only open gates

                    double d2 = base.getSquaredDistance(center);
                    if (d2 >= bestD2) continue;

                    bestD2 = d2;
                    bestBase = base.toImmutable();
                    bestState = bs;
                    bestFacing = bs.get(DimensionalGateBlock.FACING);

                    BlockEntity be = world.getBlockEntity(bestBase);
                    bestStyle = (be instanceof DimensionalGateBlockEntity gateBe) ? gateBe.getStyleId() : null;
                }
            }
        }

        if (bestBase == null) return null;
        return new GatePick(bestBase, bestState, bestFacing, bestStyle);
    }
}
