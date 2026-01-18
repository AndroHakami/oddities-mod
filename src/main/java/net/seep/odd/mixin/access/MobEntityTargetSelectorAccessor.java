// src/main/java/net/seep/odd/mixin/access/MobEntityTargetSelectorAccessor.java
package net.seep.odd.mixin.access;

import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MobEntity.class)
public interface MobEntityTargetSelectorAccessor {
    @Accessor("targetSelector")
    GoalSelector odd$getTargetSelector();
}
