package net.seep.odd.mixin;

import net.minecraft.block.FireBlock;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.server.world.ServerWorld;

import net.seep.odd.Oddities;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FireBlock.class)
public abstract class FireNoSpreadInRottenRootsMixin {

    private static final Identifier ROTTEN_ROOTS_DIM = new Identifier(Oddities.MOD_ID, "rotten_roots");

    /**
     * Prevent fire from spreading / consuming blocks in Rotten Roots only.
     * Fire can still exist, but it won't propagate or burn stuff down.
     */
    @Inject(method = "trySpreadingFire", at = @At("HEAD"), cancellable = true)
    private void odd$noSpreadInRottenRoots(World world, BlockPos pos, int spreadFactor, Random random, int currentAge, CallbackInfo ci) {
        if (world instanceof ServerWorld sw && sw.getRegistryKey().getValue().equals(ROTTEN_ROOTS_DIM)) {
            ci.cancel();
        }
    }
}