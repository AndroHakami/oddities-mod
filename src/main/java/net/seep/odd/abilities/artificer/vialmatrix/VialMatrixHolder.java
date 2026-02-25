// FILE: src/main/java/net/seep/odd/abilities/artificer/vialmatrix/VialMatrixHolder.java
package net.seep.odd.abilities.artificer.vialmatrix;

import net.minecraft.nbt.NbtCompound;

public interface VialMatrixHolder {
    NbtCompound odd$getVialMatrixData();
    void odd$setVialMatrixData(NbtCompound nbt);
}