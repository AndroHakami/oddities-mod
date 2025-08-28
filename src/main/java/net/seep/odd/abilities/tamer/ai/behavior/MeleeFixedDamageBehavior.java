package net.seep.odd.abilities.tamer.ai.behavior;

import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.seep.odd.abilities.tamer.ai.SharedGoals;

public class MeleeFixedDamageBehavior implements CompanionBehavior {
    private final double speed;
    private final float damage;
    private final int cooldownTicks;

    public MeleeFixedDamageBehavior(double speed, float damage, int cooldownTicks) {
        this.speed = speed;
        this.damage = damage;
        this.cooldownTicks = cooldownTicks;
    }

    @Override
    public void apply(MobEntity mob, ServerPlayerEntity owner, GoalSelector goals, GoalSelector targets) {
        if (mob instanceof PathAwareEntity paw) {
            goals.add(2, new SharedGoals.FixedDamageMeleeGoal(paw, speed, damage, cooldownTicks));
        }
    }
}
