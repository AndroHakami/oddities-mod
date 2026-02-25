// FILE: src/main/java/net/seep/odd/mixin/DismantleMiningMixin.java
package net.seep.odd.mixin.artificer;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;

import net.seep.odd.abilities.artificer.mixer.brew.DismantleEffect;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractBlock.AbstractBlockState.class)
// If your IDE still complains, use this instead:
// @Mixin(targets = "net.minecraft.block.AbstractBlock$AbstractBlockState")
public abstract class DismantleMiningMixin {

    @Inject(method = "calcBlockBreakingDelta", at = @At("RETURN"), cancellable = true)
    private void odd$dismantleIgnoreToolPenalty(PlayerEntity player, BlockView world, BlockPos pos,
                                                CallbackInfoReturnable<Float> cir) {
        if (player == null) return;
        if (player.getWorld().isClient) return; // server authoritative

        BlockState state = (BlockState)(Object)this;

        // don’t touch unbreakables (bedrock etc.)
        if (!net.seep.odd.abilities.artificer.mixer.brew.DismantleEffect.canDismantle(state, world, pos)) return;

        // only inside your dismantle cube (player + block)
        if (!net.seep.odd.abilities.artificer.mixer.brew.DismantleEffect
                .isPlayerAndBlockInside(player.getWorld(), player.getPos(), pos)) return;

        float v = cir.getReturnValueF();
        if (v <= 0.0f) return;

        // ✅ cancel vanilla wrong-tool penalty (100 vs 30)
        ItemStack tool = player.getMainHandStack();
        boolean suitable = tool != null && tool.isSuitableFor(state);
        if (!suitable) {
            v *= (100.0f / 30.0f);
        }

        // ✅ strong boost so obsidian becomes quick even bare-handed
        // (tweak: 120–220 feels good; 150 is a solid default)
        v *= 150.0f;

        cir.setReturnValue(v);
    }
}
