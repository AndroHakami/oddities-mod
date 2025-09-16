package net.seep.odd.abilities.artificer.mixer;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.minecraft.util.math.Direction;
import net.seep.odd.block.ModBlocks;

/** Fabric Transfer bridge so pipes can insert/extract from the mixer tanks. */
public final class MixerFluidStorage {
    private MixerFluidStorage() {}

    public static void register() {
        // Expose the BE's combined Storage<FluidVariant> on all sides
        FluidStorage.SIDED.registerForBlockEntity(
                (PotionMixerBlockEntity be, Direction dir) -> be.getFluidStorage(),
                ModBlocks.POTION_MIXER_BE
        );
    }
}
