package net.seep.odd.abilities.artificer.mixer;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.util.math.Direction;

public final class MixerFluidStorage {
    private MixerFluidStorage(){}

    public static void register() {
        FluidStorage.SIDED.registerForBlockEntity(
                (be, dir) -> be.combinedStorage(),
                ModBlockEntities.POTION_MIXER_CONTROLLER
        );
    }
}
