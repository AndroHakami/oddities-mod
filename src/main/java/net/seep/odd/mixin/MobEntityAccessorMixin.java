package net.seep.odd.mixin;

import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.mob.MobEntity;
import net.seep.odd.abilities.tamer.MobEntityAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(MobEntity.class)
public abstract class MobEntityAccessorMixin implements MobEntityAccessor {
    @Shadow public GoalSelector goalSelector;
    @Shadow public GoalSelector targetSelector;

    @Override public GoalSelector odd$getGoalSelector()  { return goalSelector; }
    @Override public GoalSelector odd$getTargetSelector(){ return targetSelector; }
}