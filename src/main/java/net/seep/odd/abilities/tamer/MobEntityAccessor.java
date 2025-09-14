package net.seep.odd.abilities.tamer;

import net.minecraft.entity.ai.goal.GoalSelector;

/** Accessor for MobEntity.goalSelector / targetSelector via mixin. */
public interface MobEntityAccessor {
    GoalSelector odd$getGoalSelector();
    GoalSelector odd$getTargetSelector();
}
