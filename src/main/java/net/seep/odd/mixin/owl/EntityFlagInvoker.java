package net.seep.odd.mixin.owl;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Entity.class)
public interface EntityFlagInvoker {
    @Invoker("setFlag")
    void odd$setFlag(int index, boolean value);
}
