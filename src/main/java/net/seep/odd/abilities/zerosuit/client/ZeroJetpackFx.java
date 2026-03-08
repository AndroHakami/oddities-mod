package net.seep.odd.abilities.zerosuit.client;

import ladysnake.satin.api.event.ShaderEffectRenderCallback;
import ladysnake.satin.api.managed.ManagedShaderEffect;
import ladysnake.satin.api.managed.ShaderEffectManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.seep.odd.Oddities;
import net.seep.odd.sound.ModSounds;

@Environment(EnvType.CLIENT)
public final class ZeroJetpackFx {
    private ZeroJetpackFx() {}

    private static boolean inited = false;
    private static ManagedShaderEffect effect;

    // desired state
    private static boolean enabled = false;   // toggled on/off
    private static boolean thrusting = false; // space currently applying thrust
    private static float fuel01 = 1.0f;       // 0..1

    // smoothed intensity so overlay “boots” in/out
    private static float intensity = 0.0f;

    // sound
    private static JetpackFlyLoop flyLoop = null;
    public static void onHud(boolean enabled, int fuel, int max, boolean thrusting) {
        init();
        float fuel01 = (max <= 0) ? 0f : MathHelper.clamp(fuel / (float) max, 0f, 1f);
        setState(enabled, thrusting, fuel01);
    }

    public static void init() {
        if (inited) return;
        inited = true;

        effect = ShaderEffectManager.getInstance()
                .manage(new Identifier(Oddities.MOD_ID, "shaders/post/zero_jetpack.json"));

        ShaderEffectRenderCallback.EVENT.register(tickDelta -> {
            if (effect == null) return;

            // smooth in/out; this is what makes it feel like “Jarvis on”
            float target = enabled ? 1.0f : 0.0f;
            intensity = MathHelper.lerp(0.20f, intensity, target);

            if (intensity <= 0.003f) return;

            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.getWindow() == null) return;

            float timeSec = (mc.world != null) ? (mc.world.getTime() + tickDelta) / 20.0f : 0.0f;

            // uniforms (safe: Satin handles these names from json)
            setUniform("iTime", timeSec);
            setUniform("Intensity", intensity);

            // either Fuel or Charge works (shader uses max)
            setUniform("Fuel", fuel01);
            setUniform("Charge", fuel01);

            // keep aspect correct
            setUniform("OutSize", (float) mc.getWindow().getFramebufferWidth(),
                    (float) mc.getWindow().getFramebufferHeight());

            effect.render(tickDelta);
        });
    }

    /**
     * Call this every client tick from your jetpack logic.
     * enabled = jetpack toggled on
     * thrusting = currently applying thrust (space held AND fuel > 0 AND not grounded if you want)
     * fuel01 = 0..1
     */
    public static void setState(boolean enabledNow, boolean thrustingNow, float fuelNow01) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;

        boolean prevEnabled = enabled;

        enabled = enabledNow;
        fuel01 = MathHelper.clamp(fuelNow01, 0.0f, 1.0f);

        // thrust only if enabled + has fuel
        thrusting = thrustingNow && enabled && (fuel01 > 0.001f);

        // toggle sound (activate / deactivate)
        if (enabled != prevEnabled && mc.player != null) {
            float pitch = enabled ? 1.0f : 0.65f; // deactivation = low pitch
            mc.player.playSound(ModSounds.JETPACK_ACTIVATE, 1.0f, pitch);
        }

        // manage loop
        if (thrusting) {
            startLoop();
        } else {
            stopLoopSoft();
        }

        // hard-stop safety: if disabled, ALWAYS stop loop
        if (!enabled) {
            stopLoopHard();
        }
    }

    /* ============================ loop sound ============================ */

    private static void startLoop() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.getSoundManager() == null) return;

        if (flyLoop == null) {
            flyLoop = new JetpackFlyLoop();
            mc.getSoundManager().play(flyLoop);
        }
        flyLoop.setTargetOn(true);
    }

    private static void stopLoopSoft() {
        if (flyLoop != null) flyLoop.setTargetOn(false);
    }

    private static void stopLoopHard() {
        if (flyLoop != null) flyLoop.stopNow();
        flyLoop = null;
    }

    /* ============================ uniform helper ============================ */

    private static void setUniform(String name, float v) {
        if (effect == null) return;
        try { effect.setUniformValue(name, v); } catch (Throwable ignored) {}
    }

    private static void setUniform(String name, float v0, float v1) {
        if (effect == null) return;
        try { effect.setUniformValue(name, v0, v1); } catch (Throwable ignored) {}
    }

    /* ============================ sound impl ============================ */

    private static final class JetpackFlyLoop extends MovingSoundInstance {
        private boolean stopped = false;
        private boolean targetOn = true;

        private float curVol = 0.0f;

        JetpackFlyLoop() {
            super(ModSounds.JETPACK_FLYING, SoundCategory.PLAYERS, net.minecraft.util.math.random.Random.create());
            this.repeat = true;
            this.repeatDelay = 0;

            // “from me” (no distance attenuation weirdness)
            this.attenuationType = SoundInstance.AttenuationType.NONE;

            this.volume = 0.0f;
            this.pitch = 1.0f;
        }

        void setTargetOn(boolean on) { this.targetOn = on; }

        void stopNow() {
            this.targetOn = false;
            this.curVol = 0.0f;
            this.volume = 0.0f;
            this.stopped = true;
        }

        @Override
        public void tick() {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.player == null || mc.world == null) { stopped = true; return; }
            if (!enabled) { stopped = true; return; }

            // follow player (from self)
            this.x = mc.player.getX();
            this.y = mc.player.getY();
            this.z = mc.player.getZ();

            // fade in/out QUICKLY
            float target = (targetOn && thrusting && fuel01 > 0.001f) ? 1.0f : 0.0f;
            curVol = MathHelper.lerp(0.35f, curVol, target); // fast smoothing
            this.volume = curVol;

            // a bit of pitch motion so it feels alive
            this.pitch = 0.95f + 0.12f * (float)MathHelper.clamp(mc.player.getVelocity().length() / 1.1, 0.0, 1.0);

            // when faded out, end it
            if (target == 0.0f && curVol < 0.02f) stopped = true;
        }

        @Override
        public boolean isDone() {
            return stopped;
        }
    }
}