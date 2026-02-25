package net.seep.odd.abilities.artificer.mixer;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.seep.odd.abilities.init.ArtificerMixerRegistry;

public final class MixerFluidStorage {
    private MixerFluidStorage() {}

    public static void register() {
        // Controller BE storage (center block)
        FluidStorage.SIDED.registerForBlockEntity(
                (PotionMixerBlockEntity be, Direction dir) -> be.externalCombinedStorage(),
                ArtificerMixerRegistry.POTION_MIXER_BE
        );

        // Any part forwards to controller
        FluidStorage.SIDED.registerForBlocks((world, pos, state, blockEntity, dir) -> {
            if (!(state.getBlock() instanceof PotionMixerMegaBlock)) return null;
            BlockPos c = PotionMixerMegaBlock.getControllerPos(pos, state);
            BlockEntity be = world.getBlockEntity(c);
            if (be instanceof PotionMixerBlockEntity mix) return mix.externalCombinedStorage();
            return null;
        }, ArtificerMixerRegistry.POTION_MIXER);
    }
}
