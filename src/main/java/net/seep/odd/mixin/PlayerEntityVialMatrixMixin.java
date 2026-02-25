// FILE: src/main/java/net/seep/odd/mixin/player/PlayerEntityVialMatrixMixin.java
package net.seep.odd.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;

import net.seep.odd.abilities.artificer.vialmatrix.VialMatrixConstants;
import net.seep.odd.abilities.artificer.vialmatrix.VialMatrixHolder;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityVialMatrixMixin implements VialMatrixHolder {

    @Unique
    private NbtCompound odd$vialMatrix = new NbtCompound();

    @Override
    public NbtCompound odd$getVialMatrixData() {
        return odd$vialMatrix;
    }

    @Override
    public void odd$setVialMatrixData(NbtCompound nbt) {
        odd$vialMatrix = (nbt == null) ? new NbtCompound() : nbt;
    }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void odd$writeVialMatrix(NbtCompound nbt, CallbackInfo ci) {
        nbt.put(VialMatrixConstants.NBT_KEY, odd$vialMatrix);
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void odd$readVialMatrix(NbtCompound nbt, CallbackInfo ci) {
        if (nbt.contains(VialMatrixConstants.NBT_KEY, NbtElement.COMPOUND_TYPE)) {
            odd$vialMatrix = nbt.getCompound(VialMatrixConstants.NBT_KEY);
        } else {
            odd$vialMatrix = new NbtCompound();
        }
    }
}