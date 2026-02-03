package net.seep.odd.mixin.owl;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.seep.odd.abilities.power.OwlPower;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityStepSoundMixin {
    @Inject(method = "playStepSound", at = @At("HEAD"), cancellable = true)
    private void odd$owlQuietSteps(BlockPos pos, BlockState state, CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity)(Object)this;
        if (!OwlPower.hasOwl(player)) return;

        BlockSoundGroup group = state.getSoundGroup();
        float vol = group.getVolume() * 0.08f; // VERY quiet
        float pitch = group.getPitch();

        player.getWorld().playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                group.getStepSound(),
                SoundCategory.PLAYERS,
                vol,
                pitch
        );

        ci.cancel();
    }
}
