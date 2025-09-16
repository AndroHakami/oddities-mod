package net.seep.odd.abilities.artificer.mixer;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.minecraft.util.math.Direction;
import net.seep.odd.abilities.init.ArtificerMixerRegistry;

/** Fabric Transfer exposure for the Potion Mixer. */
public final class MixerFluidStorage {
    private MixerFluidStorage() {}

    /** Call once after POTION_MIXER_BE is registered (or keep the inline call in the registry). */
    public static void register() {
        FluidStorage.SIDED.registerForBlockEntity(
                (PotionMixerBlockEntity be, Direction dir) -> be.externalCombinedStorage(),
                ArtificerMixerRegistry.POTION_MIXER_BE
        );
    }
}
