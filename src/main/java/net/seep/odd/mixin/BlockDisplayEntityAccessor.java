// src/main/java/net/seep/odd/mixin/BlockDisplayEntityAccessor.java
package net.seep.odd.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.entity.decoration.DisplayEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(DisplayEntity.BlockDisplayEntity.class)
public interface BlockDisplayEntityAccessor {
    @Invoker("setBlockState")
    void odd$setBlockState(BlockState state);
}
