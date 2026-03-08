package net.seep.odd.abilities.lunar.client;

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
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;

import net.seep.odd.Oddities;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public final class LunarDrillPreviewFx implements PostWorldRenderCallback {
    private LunarDrillPreviewFx() {}

    private static final Identifier POST_ID = new Identifier(Oddities.MOD_ID, "shaders/post/lunar_drill_preview.json");
    private static boolean inited = false;

    private static final MinecraftClient client = MinecraftClient.getInstance();
    private static final Matrix4f tmpMat = new Matrix4f();

    private static ManagedShaderEffect shader;

    private static UniformMat4 uInvTransform;
    private static Uniform3f  uCameraPos;

    private static Uniform3f  uOrigin;
    private static Uniform3f  uUAxis;
    private static Uniform3f  uVAxis;
    private static Uniform3f  uIntoAxis;

    private static Uniform1f  uDepth;

    private static Uniform1f  uMask0;
    private static Uniform1f  uMask1;
    private static Uniform1f  uMask2;
    private static Uniform1f  uMask3;

    private static Uniform1f  uTime;
    private static Uniform1f  uIntensity;
    private static Uniform1f  uCharge;

    // ===== state driven by the drill item while charging =====
    private static boolean active = false;

    private static float originX, originY, originZ;
    private static float ux, uy, uz;
    private static float vx, vy, vz;
    private static float ix, iy, iz;

    private static float depth = 1f;

    private static float mask0, mask1, mask2, mask3; // 0..255 in floats
    private static float charge01 = 0f;

    // fade
    private static float strength = 0f;
    private static float target = 0f;

    // ✅ NEW: auto-clear if nobody updates us (fixes “stuck visible for other players”)
    private static final int STALE_TICKS = 3;
    private static long lastPatternAt = -999999L;

    public static void init() {
        if (inited) return;
        inited = true;
        PostWorldRenderCallback.EVENT.register(new LunarDrillPreviewFx());
    }

    /** Clear preview (fade out smoothly). */
    public static void clear() {
        active = false;
        target = 0f;
        charge01 = 0f;
        depth = 1f;
        // NOTE: we intentionally do NOT wipe masks/origin here every frame;
        // shader keeps last values for a nice fade-out.
        lastPatternAt = -999999L;
    }

    /**
     * Call this every tick while charging.
     * This EXACTLY matches your layoutPatternWithDepth mapping:
     * base = origin.offset(u, dc).offset(v, -dr).offset(into, k)
     */
    public static void setPattern(BlockPos origin, Direction face, Direction horizontal, long rawMask, int depthBlocks, float chargePct) {
        if (origin == null) { clear(); return; }

        // compute U/V exactly like LunarDrillItem.layoutPatternWithDepth(...)
        Direction u, v;
        switch (face) {
            case NORTH -> { u = Direction.EAST;  v = Direction.UP; }
            case SOUTH -> { u = Direction.WEST;  v = Direction.UP; }
            case WEST  -> { u = Direction.SOUTH; v = Direction.UP; }
            case EAST  -> { u = Direction.NORTH; v = Direction.UP; }
            default -> {
                switch (horizontal) {
                    case NORTH -> { u = Direction.EAST;  v = Direction.NORTH; }
                    case SOUTH -> { u = Direction.WEST;  v = Direction.SOUTH; }
                    case WEST  -> { u = Direction.SOUTH; v = Direction.WEST; }
                    default    -> { u = Direction.NORTH; v = Direction.EAST; }
                }
            }
        }
        Direction into = face.getOpposite();

        originX = origin.getX();
        originY = origin.getY();
        originZ = origin.getZ();

        ux = u.getOffsetX();
        uy = u.getOffsetY();
        uz = u.getOffsetZ();

        vx = v.getOffsetX();
        vy = v.getOffsetY();
        vz = v.getOffsetZ();

        ix = into.getOffsetX();
        iy = into.getOffsetY();
        iz = into.getOffsetZ();

        depth = MathHelper.clamp(depthBlocks, 1, 6);

        long mask = net.seep.odd.abilities.lunar.item.LunarDrillItem.normalizeMask(rawMask);

        // pack mask into 4 bytes (0..255) as floats
        mask0 = (float) ( mask        & 0xFFL);
        mask1 = (float) ((mask >>  8) & 0xFFL);
        mask2 = (float) ((mask >> 16) & 0xFFL);
        mask3 = (float) ((mask >> 24) & 0xFFL);

        charge01 = MathHelper.clamp(chargePct, 0f, 1f);

        active = true;
        target = 1f;

        // ✅ remember last update time so other clients can auto-fade when you stop
        if (client != null && client.world != null) {
            lastPatternAt = client.world.getTime();
        } else if (client != null && client.player != null) {
            lastPatternAt = client.player.age;
        }
    }

    private static void ensureInit() {
        if (shader != null) return;

        shader = ShaderEffectManager.getInstance().manage(POST_ID, s -> {
            if (client != null && client.getFramebuffer() instanceof ReadableDepthFramebuffer rdf) {
                s.setSamplerUniform("DepthSampler", rdf.getStillDepthMap());
            }
        });

        uInvTransform = shader.findUniformMat4("InverseTransformMatrix");
        uCameraPos    = shader.findUniform3f("CameraPosition");

        uOrigin   = shader.findUniform3f("Origin");
        uUAxis    = shader.findUniform3f("UAxis");
        uVAxis    = shader.findUniform3f("VAxis");
        uIntoAxis = shader.findUniform3f("IntoAxis");

        uDepth = shader.findUniform1f("Depth");

        uMask0 = shader.findUniform1f("Mask0");
        uMask1 = shader.findUniform1f("Mask1");
        uMask2 = shader.findUniform1f("Mask2");
        uMask3 = shader.findUniform1f("Mask3");

        uTime      = shader.findUniform1f("iTime");
        uIntensity = shader.findUniform1f("Intensity");
        uCharge    = shader.findUniform1f("Charge");
    }

    @Override
    public void onWorldRendered(Camera camera, float tickDelta, long limitTime) {
        if (client == null || client.world == null) return;

        ClientWorld world = client.world;

        // ✅ NEW: if nobody updated the pattern recently, fade it out automatically
        if (active) {
            long now = world.getTime();
            if (now - lastPatternAt > STALE_TICKS) {
                // don't hard-clear uniforms; just stop “active” and fade out smoothly
                active = false;
                target = 0f;
                charge01 = 0f;
            }
        }

        // smooth fade
        strength += (target - strength) * 0.22f;
        if (strength < 0.001f) return;

        ensureInit();
        if (shader == null) return;

        var camPos = camera.getPos();
        uInvTransform.set(GlMatrices.getInverseTransformMatrix(tmpMat));
        uCameraPos.set((float) camPos.x, (float) camPos.y, (float) camPos.z);

        float time = ((client.player != null ? client.player.age : 0) + tickDelta) / 20.0f;
        uTime.set(time);

        uIntensity.set(strength);

        // If we aren't active anymore, just fade out (keep last uniforms)
        if (!active) {
            shader.render(tickDelta);
            return;
        }

        // pattern uniforms
        uOrigin.set(originX, originY, originZ);
        uUAxis.set(ux, uy, uz);
        uVAxis.set(vx, vy, vz);
        uIntoAxis.set(ix, iy, iz);

        uDepth.set(depth);

        uMask0.set(mask0);
        uMask1.set(mask1);
        uMask2.set(mask2);
        uMask3.set(mask3);

        uCharge.set(charge01);

        shader.render(tickDelta);
    }
}