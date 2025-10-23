package net.seep.odd.abilities.init;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.minecraft.client.gui.screen.ingame.HandledScreens;

import net.seep.odd.abilities.artificer.mixer.MixerNet;
import net.seep.odd.abilities.artificer.mixer.PotionMixerScreenHandler;
import net.seep.odd.abilities.artificer.mixer.client.PotionMixerScreen;

@Environment(EnvType.CLIENT)
public final class ArtificerMixerClient {
    private ArtificerMixerClient() {}

    private static boolean REGISTERED_CLIENT = false;

    public static void registerClient() {
        if (REGISTERED_CLIENT) return;

        // Screen
        HandledScreens.register(
                ArtificerMixerRegistry.POTION_MIXER_SH,
                (PotionMixerScreenHandler h, net.minecraft.entity.player.PlayerInventory inv,
                 net.minecraft.text.Text title) -> new PotionMixerScreen(h, inv, title)
        );

        // Item tint registration — overlay tint at index 1
        if (ArtificerMixerRegistry.BREW_DRINKABLE != null && ArtificerMixerRegistry.BREW_THROWABLE != null) {
            ColorProviderRegistry.ITEM.register((stack, tintIndex) -> {
                if (tintIndex == 1 && stack.hasNbt()) {
                    return stack.getNbt().getInt("odd_brew_color");
                }
                return 0xFFFFFFFF;
            }, ArtificerMixerRegistry.BREW_DRINKABLE, ArtificerMixerRegistry.BREW_THROWABLE);
        }

        // Client side (symmetry; receivers if any)
        MixerNet.registerClient();

        REGISTERED_CLIENT = true;
    }
}
