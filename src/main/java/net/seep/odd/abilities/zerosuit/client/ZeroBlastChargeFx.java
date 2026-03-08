// src/main/java/net/seep/odd/abilities/zerosuit/client/ZeroBlastChargeFx.java
package net.seep.odd.abilities.zerosuit.client;

import ladysnake.satin.api.event.ShaderEffectRenderCallback;
import ladysnake.satin.api.managed.ManagedShaderEffect;
import ladysnake.satin.api.managed.ShaderEffectManager;
import ladysnake.satin.api.managed.uniform.Uniform1f;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

import net.seep.odd.Oddities;
import net.seep.odd.sound.ModSounds;

@Environment(EnvType.CLIENT)
public final class ZeroBlastChargeFx {
    private ZeroBlastChargeFx() {}

    private static final Identifier POST_ID =
            new Identifier(Oddities.MOD_ID, "shaders/post/zero_blast_charge.json");

    private static boolean inited = false;

    private static boolean active = false;
    private static int charge = 0;
    private static int max = 1;

    private static float master = 0f;     // fades in/out
    private static float smoothPct = 0f;  // charge smoothing

    private static ManagedShaderEffect shader;
    private static Uniform1f uTime, uIntensity, uCharge, uZoom;

    // === NEW: local loop sound so the caster can hear charging ===
    private static ChargeLoopSound loop;

    public static void init() {
        if (inited) return;
        inited = true;

        ShaderEffectRenderCallback.EVENT.register(ZeroBlastChargeFx::render);
        WorldRenderEvents.START.register(ScreenShake::applyTransform);

        HudRenderCallback.EVENT.register((DrawContext ctx, float tickDelta) -> {
            if (!isActive()) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            int sw = mc.getWindow().getScaledWidth();
            int sh = mc.getWindow().getScaledHeight();

            int pct = (int)(MathHelper.clamp(smoothPct, 0f, 1f) * 100f);
            String s = pct + "%";
            int w = mc.textRenderer.getWidth(s);

            ctx.drawText(mc.textRenderer, s, sw / 2 - w / 2, sh / 2 - 4, 0xFFFFFFFF, true);
        });
    }

    private static void ensureInit() {
        if (shader != null) return;

        shader = ShaderEffectManager.getInstance().manage(POST_ID);
        uTime = shader.findUniform1f("iTime");
        uIntensity = shader.findUniform1f("Intensity");
        uCharge = shader.findUniform1f("Charge");
        uZoom = shader.findUniform1f("Zoom");
    }

    public static void onHud(boolean s, int c, int m) {
        active = s;
        charge = Math.max(0, c);
        max = Math.max(1, m);

        // Start/stop local loop immediately when HUD state changes
        if (active) startLoopIfNeeded();
        else stopLoopNow();
    }

    public static boolean isActive() {
        return master > 0.001f;
    }

    public static void stop() {
        active = false;
        charge = 0;
        max = 1;
        master = 0f;
        smoothPct = 0f;
        stopLoopNow();
    }

    public static void kickFireShake(float ratio) {
        float r = MathHelper.clamp(ratio, 0f, 1f);
        ScreenShake.kick(1.9f + 1.6f * r, 18);
    }

    private static void tick(float tickDelta) {
        float targetMaster = active ? 1.0f : 0.0f;
        master = MathHelper.lerp(0.22f, master, targetMaster);

        float pct = (max > 0) ? MathHelper.clamp(charge / (float) max, 0f, 1f) : 0f;
        smoothPct = MathHelper.lerp(0.18f, smoothPct, pct);

        if (!active && master < 0.01f) {
            master = 0f;
            smoothPct = 0f;
        }

        // Keep loop alive while active; it will self-stop if invalid
        if (active) startLoopIfNeeded();
    }

    private static void render(float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null) { stop(); return; }
        if (mc.isPaused()) { stopLoopNow(); return; }

        tick(tickDelta);
        if (master <= 0.001f) return;

        ensureInit();

        float t = (mc.player != null ? (mc.player.age + tickDelta) : (float)(System.nanoTime() / 1e9)) / 20.0f;
        float zoom = 1.0f + 0.035f * smoothPct;

        if (uTime != null) uTime.set(t);
        if (uIntensity != null) uIntensity.set(master);
        if (uCharge != null) uCharge.set(smoothPct);
        if (uZoom != null) uZoom.set(zoom);

        shader.render(tickDelta);
    }

    private static void startLoopIfNeeded() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.getSoundManager() == null) return;
        if (mc.isPaused()) return;

        if (loop == null || loop.isDone()) {
            loop = new ChargeLoopSound(mc.player);
            mc.getSoundManager().play(loop);
        }
    }

    private static void stopLoopNow() {
        if (loop != null) loop.stopNow();
        loop = null;
    }

    private static final class ChargeLoopSound extends MovingSoundInstance {
        private final net.minecraft.entity.player.PlayerEntity player;
        private boolean stopped = false;

        ChargeLoopSound(net.minecraft.entity.player.PlayerEntity player) {
            super(ModSounds.ZERO_CHARGE, SoundCategory.PLAYERS, Random.create());
            this.player = player;
            this.repeat = true;
            this.repeatDelay = 0;

            this.volume = 0.25f;
            this.pitch  = 0.95f;

            // We want it to behave like a “personal charging” sound
            this.attenuationType = SoundInstance.AttenuationType.LINEAR;

            this.x = player.getX();
            this.y = player.getY();
            this.z = player.getZ();
        }

        @Override
        public void tick() {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.isPaused()) { stopped = true; return; }
            if (player == null || player.isRemoved() || !active) { stopped = true; return; }

            this.x = player.getX();
            this.y = player.getY();
            this.z = player.getZ();

            float pct = MathHelper.clamp(smoothPct, 0f, 1f);
            this.volume = 0.18f + 0.95f * pct;
            this.pitch  = 0.92f + 0.30f * pct;
        }

        @Override public boolean isDone() { return stopped; }
        void stopNow() { stopped = true; }
    }

    private static final class ScreenShake {
        private static float strength;
        private static int   ticks;
        private static long  seed = 1337L;

        static void kick(float s, int dur) {
            strength = Math.max(strength, s);
            ticks = Math.max(ticks, dur);
            seed ^= (System.nanoTime() + dur);
        }

        private static float noise(long n) {
            n = (n << 13) ^ n;
            return (1.0f - ((n * (n * n * 15731L + 789221L) + 1376312589L) & 0x7fffffff) / 1073741824.0f);
        }

        static void applyTransform(WorldRenderContext ctx) {
            if (ticks <= 0 || strength <= 0f) return;

            float td = ctx.tickDelta();
            var mc = MinecraftClient.getInstance();
            long tBase = (mc != null && mc.player != null) ? mc.player.age : 0L;
            long t = tBase + (long) (td * 10.0f);

            float n1 = noise(seed + t * 3L) * 0.5f;
            float n2 = noise(seed ^ (t * 5L)) * 0.5f;

            float s = strength * 0.020f;
            ctx.matrixStack().translate(n1 * s, n2 * s, 0.0f);

            ticks--;
            strength *= 0.90f;
            if (ticks <= 0 || strength < 0.02f) { ticks = 0; strength = 0f; }
        }
    }
}