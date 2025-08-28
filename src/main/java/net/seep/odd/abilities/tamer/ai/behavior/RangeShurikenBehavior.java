package net.seep.odd.abilities.tamer.ai.behavior;

import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.seep.odd.abilities.tamer.ai.SharedGoals;

public class RangeShurikenBehavior implements CompanionBehavior {
    private final double moveSpeed, minRange, maxRange, orbitRadius;
    private final int cooldownTicks;

    public RangeShurikenBehavior(double moveSpeed, double minRange, double maxRange, int cooldownTicks, double orbitRadius) {
        this.moveSpeed = moveSpeed;
        this.minRange = minRange;
        this.maxRange = maxRange;
        this.cooldownTicks = cooldownTicks;
        this.orbitRadius = orbitRadius;
    }

    @Override
    public void apply(MobEntity mob, ServerPlayerEntity owner, GoalSelector goals, GoalSelector targets) {
        if (mob instanceof PathAwareEntity paw) {
            goals.add(2, new SharedGoals.RangedKiteShurikenGoal(paw, moveSpeed, minRange, maxRange, cooldownTicks, orbitRadius));
        }
    }
}
