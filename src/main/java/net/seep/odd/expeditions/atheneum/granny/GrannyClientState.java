package net.seep.odd.expeditions.atheneum.granny;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.seep.odd.entity.granny.GrannyEntity;
import net.seep.odd.expeditions.atheneum.granny.client.GrannyFx;
import net.seep.odd.sound.ModSounds;


import java.util.List;

public final class GrannyClientState {
    private static final RegistryKey<World> ATHENEUM =
            RegistryKey.of(RegistryKeys.WORLD, new Identifier("odd", "atheneum"));

    private static boolean active = false;
    private static float intensity = 0.0f;
    private static float targetIntensity = 0.0f;

    private static SoundInstance themeInstance;
    private static int quietTitleTicks = 0;

    private GrannyClientState() {}

    public static void initClient() {
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> renderQuietTitle(drawContext, tickDelta));
    }

    public static void setActive(boolean value) {
        active = value;
        if (!value) stopTheme();
    }

    public static void triggerSpawnCue() {
        quietTitleTicks = 40;
    }

    public static void clientTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || mc.player == null) {
            active = false;
            targetIntensity = 0.0f;
            intensity = MathHelper.lerp(0.35f, intensity, 0.0f);
            stopTheme();
            GrannyFx.setIntensityTarget(0.0f);
            quietTitleTicks = Math.max(0, quietTitleTicks - 1);
            return;
        }

        PlayerEntity player = mc.player;
        boolean inAtheneum = mc.world.getRegistryKey().equals(ATHENEUM);

        if (!active || !inAtheneum) {
            targetIntensity = 0.0f;
            intensity = MathHelper.lerp(0.25f, intensity, 0.0f);
            GrannyFx.setIntensityTarget(intensity);
            stopTheme();
            quietTitleTicks = Math.max(0, quietTitleTicks - 1);
            return;
        }

        keepThemeAlive();

        List<GrannyEntity> grannies = mc.world.getEntitiesByClass(
                GrannyEntity.class,
                new Box(player.getBlockPos()).expand(64.0D),
                g -> g.isAlive()
        );

        double bestSq = Double.MAX_VALUE;
        for (GrannyEntity granny : grannies) {
            double sq = granny.squaredDistanceTo(player);
            if (sq < bestSq) bestSq = sq;
        }

        if (bestSq == Double.MAX_VALUE) {
            targetIntensity = 0.15f;
        } else {
            double dist = Math.sqrt(bestSq);
            float closeness = 1.0f - (float)(dist / 34.0D);
            targetIntensity = MathHelper.clamp(closeness, 0.0f, 1.0f);
            targetIntensity *= targetIntensity;
        }

        intensity = MathHelper.lerp(0.18f, intensity, targetIntensity);
        GrannyFx.setIntensityTarget(intensity);
        quietTitleTicks = Math.max(0, quietTitleTicks - 1);
    }

    private static void renderQuietTitle(DrawContext drawContext, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.textRenderer == null || quietTitleTicks <= 0) return;

        float t = quietTitleTicks - tickDelta;
        float life = MathHelper.clamp(t / 40.0f, 0.0f, 1.0f);
        float fadeIn = MathHelper.clamp((40.0f - t) / 5.0f, 0.0f, 1.0f);
        float fadeOut = MathHelper.clamp(t / 8.0f, 0.0f, 1.0f);
        float alpha = fadeIn * fadeOut;
        if (alpha <= 0.01f) return;

        String text = "Quiet";
        float scale = 4.0f;
        int textWidth = mc.textRenderer.getWidth(text);
        int centerX = drawContext.getScaledWindowWidth() / 2;
        int centerY = drawContext.getScaledWindowHeight() / 3;

        float shake = 1.0f + (1.0f - life) * 3.0f;
        float time = (mc.world != null ? mc.world.getTime() : 0) + tickDelta;
        float jitterX = MathHelper.sin(time * 2.7f) * shake;
        float jitterY = MathHelper.cos(time * 3.9f) * shake * 0.65f;

        int alphaByte = MathHelper.clamp((int) (alpha * 255.0f), 0, 255);
        int color = (alphaByte << 24) | 0xFF3B3B;
        int shadow = (Math.max(0, alphaByte - 110) << 24);

        drawContext.getMatrices().push();
        drawContext.getMatrices().translate(centerX + jitterX, centerY + jitterY, 0.0f);
        drawContext.getMatrices().scale(scale, scale, 1.0f);
        drawContext.drawText(mc.textRenderer, text, -textWidth / 2 + 1, 1, shadow, false);
        drawContext.drawText(mc.textRenderer, text, -textWidth / 2, 0, color, true);
        drawContext.getMatrices().pop();
    }

    private static void keepThemeAlive() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getSoundManager() == null) return;

        if (themeInstance == null || !mc.getSoundManager().isPlaying(themeInstance)) {
            themeInstance = PositionedSoundInstance.master(ModSounds.GRANNY_THEME, 1.0f, 1.0f);
            mc.getSoundManager().play(themeInstance);
        }
    }

    private static void stopTheme() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getSoundManager() == null || themeInstance == null) return;
        mc.getSoundManager().stop(themeInstance);
        themeInstance = null;
    }
}