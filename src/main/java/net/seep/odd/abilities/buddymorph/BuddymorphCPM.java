package net.seep.odd.abilities.buddymorph;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.seep.odd.abilities.overdrive.client.CpmHooks;

public final class BuddymorphCPM {
    private static final boolean CPM = FabricLoader.getInstance().isModLoaded("cpm");
    private BuddymorphCPM(){}

    public static void playMelody(PlayerEntity player) {
        if (!CPM || player == null) return;
        try {
            // your project-side hook; replace with your actual CPM call if different
            CpmHooks.play("melody");
        } catch (Throwable ignored) {}
    }
}
