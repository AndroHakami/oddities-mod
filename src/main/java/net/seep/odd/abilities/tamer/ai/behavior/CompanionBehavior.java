package net.seep.odd.abilities.tamer.ai.behavior;

import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;

@FunctionalInterface
public interface CompanionBehavior {
    /** Add any goals you want to the provided selectors. */
    void apply(MobEntity mob, ServerPlayerEntity owner, GoalSelector goals, GoalSelector targets);
}
