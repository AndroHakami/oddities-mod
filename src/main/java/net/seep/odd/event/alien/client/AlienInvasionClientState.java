package net.seep.odd.event.alien.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.world.World;
import net.seep.odd.event.alien.client.sound.AlienInvasionMusic;

public final class AlienInvasionClientState {
    private AlienInvasionClientState() {}

    private static boolean ACTIVE = false;
    private static int WAVE = 0;
    private static int MAX = 0;

    private static int ageTicks = 0;

    public static boolean active() { return ACTIVE; }
    public static int wave() { return WAVE; }
    public static int maxWaves() { return MAX; }

    public static int ageTicks() { return ageTicks; }

    public static void setState(boolean active, int wave, int max) {
        ACTIVE = active;
        WAVE = wave;
        MAX = max;
        if (!ACTIVE) ageTicks = 0;
    }

    public static void clientTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        boolean inOverworld = mc.world.getRegistryKey() == World.OVERWORLD;

        if (ACTIVE && inOverworld) {
            ageTicks++;
            AlienInvasionMusic.ensurePlaying();
        } else {
            AlienInvasionMusic.stop();
            if (!ACTIVE) ageTicks = 0;
        }
    }

    // 0..1: green darkness fade-in
    public static float skyProgress01(float tickDelta) {
        float t = (ageTicks + tickDelta);
        float fadeTicks = 40f;
        return Math.min(1f, t / fadeTicks);
    }

    // 0..1: cubes ramp in slightly after
    public static float cubes01(float tickDelta) {
        float t = (ageTicks + tickDelta);
        float start = 18f;
        float len = 50f;
        return Math.max(0f, Math.min(1f, (t - start) / len));
    }
}