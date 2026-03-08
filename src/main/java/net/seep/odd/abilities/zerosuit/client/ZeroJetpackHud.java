package net.seep.odd.abilities.zerosuit.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.sound.EntityTrackingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.MathHelper;

import net.seep.odd.sound.ModSounds;

@Environment(EnvType.CLIENT)
public final class ZeroJetpackHud {
    private ZeroJetpackHud() {}

    private static boolean inited = false;

    private static boolean enabled = false;
    private static int fuel = 0;
    private static int max = 100;
    private static boolean thrusting = false;

    // Splash-style fading loop (LOCAL PLAYER ONLY)
    private static final int FADE_IN_TICKS  = 6;
    private static final int FADE_OUT_TICKS = 8;

    private static FadingLoopSound jetpackLoop = null;

    public static void init() {
        if (inited) return;
        inited = true;

        // Stop loops hard on disconnect/join (prevents "stuck audio")
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> stopAllLoopsHard());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> stopAllLoopsHard());

        HudRenderCallback.EVENT.register((ctx, tickDelta) -> {
            if (!enabled) return;

            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null) return;

            int sw = mc.getWindow().getScaledWidth();
            int sh = mc.getWindow().getScaledHeight();

            float frac = (max <= 0) ? 0f : MathHelper.clamp(fuel / (float) max, 0f, 1f);

            // NOTE: leaving your drawing as-is here; only loop behavior changed.
            // If you want the arc position/flip changed, tell me the exact anchor you want.
            int cx = sw / 2;     // (you said "center" before — keep centered)
            int cy = sh / 2;

            drawArcGauge(ctx.getMatrices(), cx, cy, frac, thrusting, mc);
        });
    }

    public static boolean isEnabled() { return enabled; }

    /**
     * Called from your net sync:
     * ZeroJetpackHud.onHud(enabled, fuel, max, thrusting)
     */
    public static void onHud(boolean en, int f, int m, boolean th) {
        enabled = en;
        fuel = Math.max(0, f);
        max = Math.max(1, m);
        thrusting = th;

        // drive overlay (keep this call!)
        ZeroJetpackFx.onHud(enabled, fuel, max, thrusting);

        // drive local loop
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null || mc.getSoundManager() == null) return;

        // cleanup if finished
        if (jetpackLoop != null && jetpackLoop.isFinished()) {
            jetpackLoop = null;
        }

        boolean shouldPlay = enabled && thrusting && fuel > 0 && mc.player.isAlive();

        if (shouldPlay) {
            if (jetpackLoop == null) {
                float pitch = 1.00f + mc.world.random.nextFloat() * 0.06f; // tiny variation like Splash
                long seed = mc.world.random.nextLong();

                jetpackLoop = new FadingLoopSound(
                        ModSounds.JETPACK_FLYING,
                        SoundCategory.PLAYERS,
                        mc.player,
                        0.95f,      // base volume
                        pitch,
                        seed,
                        FADE_IN_TICKS,
                        FADE_OUT_TICKS
                );
                mc.getSoundManager().play(jetpackLoop);
            }
            jetpackLoop.setDesired(true);
        } else {
            if (jetpackLoop != null) jetpackLoop.setDesired(false);
        }
    }

    private static void stopAllLoopsHard() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.getSoundManager() != null) {
            if (jetpackLoop != null) mc.getSoundManager().stop(jetpackLoop);
        }
        jetpackLoop = null;
        enabled = false;
        thrusting = false;
        fuel = 0;
        max = 1;
        try {
            ZeroJetpackFx.onHud(false, 0, 1, false);
        } catch (Throwable ignored) {}
    }

    /* ======================= HUD arc drawing ======================= */

    private static void drawArcGauge(MatrixStack matrices, int cx, int cy, float frac, boolean thrust, MinecraftClient mc) {
        // 120° arc
        float a0 = (float) Math.toRadians(240.0);
        float a1 = (float) Math.toRadians(120.0);

        int segs = 32;

        // slimmer than the chunky one
        float outerR = 18f;
        float innerR = 14f;

        // colors (ARGB)
        int bg = 0x55FF7A1A;
        int fg = 0xCCFF9A3A;
        int hot = 0xFFFFD08A;

        float t = (mc.player != null ? mc.player.age : 0) / 20f;
        float pulse = thrust ? (0.85f + 0.15f * MathHelper.sin(t * 8.0f)) : 1.0f;

        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        var mat = matrices.peek().getPositionMatrix();
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder bb = tess.getBuffer();

        // background arc
        bb.begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        for (int i = 0; i <= segs; i++) {
            float u = i / (float) segs;
            float ang = MathHelper.lerp(u, a0, a1);
            float cs = MathHelper.cos(ang);
            float sn = MathHelper.sin(ang);

            float ox = cx + cs * outerR;
            float oy = cy + sn * outerR;
            float ix = cx + cs * innerR;
            float iy = cy + sn * innerR;

            put(bb, mat, ox, oy, bg);
            put(bb, mat, ix, iy, bg);
        }
        tess.draw();

        // filled arc
        int fillSegs = Math.round(segs * frac);
        if (fillSegs > 0) {
            bb.begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
            for (int i = 0; i <= fillSegs; i++) {
                float u = i / (float) segs;
                float ang = MathHelper.lerp(u, a0, a1);
                float cs = MathHelper.cos(ang);
                float sn = MathHelper.sin(ang);

                float ox = cx + cs * outerR;
                float oy = cy + sn * outerR;
                float ix = cx + cs * innerR;
                float iy = cy + sn * innerR;

                int col = thrust ? hot : fg;
                if (thrust) col = applyAlpha(col, (int)(220 * pulse));

                put(bb, mat, ox, oy, col);
                put(bb, mat, ix, iy, col);
            }
            tess.draw();
        }

        RenderSystem.disableBlend();
    }

    private static void put(BufferBuilder bb, org.joml.Matrix4f mat, float x, float y, int argb) {
        float a = ((argb >> 24) & 255) / 255f;
        float r = ((argb >> 16) & 255) / 255f;
        float g = ((argb >>  8) & 255) / 255f;
        float b = ((argb      ) & 255) / 255f;
        bb.vertex(mat, x, y, 0).color(r, g, b, a).next();
    }

    private static int applyAlpha(int argb, int alpha0to255) {
        alpha0to255 = MathHelper.clamp(alpha0to255, 0, 255);
        return (alpha0to255 << 24) | (argb & 0x00FFFFFF);
    }

    /* =======================
       Splash-style loop sound
       ======================= */

    private static final class FadingLoopSound extends EntityTrackingSoundInstance {
        private final Entity tracked;
        private final float baseVolume;
        private final int fadeInTicks;
        private final int fadeOutTicks;

        private float curVol;
        private boolean desired = true;
        private boolean finished = false;

        FadingLoopSound(SoundEvent sound, SoundCategory category, Entity entity,
                        float baseVolume, float pitch, long seed,
                        int fadeInTicks, int fadeOutTicks) {
            // Start volume must NOT be 0 for some looping instances (Splash comment)
            super(sound, category, 0.001f, pitch, entity, seed);
            this.tracked = entity;
            this.baseVolume = baseVolume;
            this.fadeInTicks = Math.max(1, fadeInTicks);
            this.fadeOutTicks = Math.max(1, fadeOutTicks);

            this.curVol = Math.min(0.08f, baseVolume);
            this.volume = this.curVol;

            this.repeat = true;
            this.repeatDelay = 0;
            this.attenuationType = SoundInstance.AttenuationType.LINEAR;
            this.relative = false;
        }

        void setDesired(boolean on) { this.desired = on; }
        boolean isFinished() { return finished; }

        @Override
        public void tick() {
            super.tick();
            if (finished) return;

            if (tracked == null || tracked.isRemoved()) {
                this.volume = 0f;
                this.setDone();
                finished = true;
                return;
            }

            float stepIn  = baseVolume / (float) fadeInTicks;
            float stepOut = baseVolume / (float) fadeOutTicks;

            if (desired) curVol = Math.min(baseVolume, curVol + stepIn);
            else         curVol = Math.max(0f, curVol - stepOut);

            this.volume = curVol;

            // little energy when thrusting (matches feel)
            this.pitch = thrusting ? 1.08f : 0.98f;

            if (!desired && curVol <= 0.0005f) {
                this.volume = 0f;
                this.setDone();
                finished = true;
            }
        }
    }
}