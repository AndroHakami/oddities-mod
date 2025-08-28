package net.seep.odd.abilities.tamer.ai.behavior;

import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.seep.odd.abilities.tamer.ai.SharedGoals;

/** Adds a charge-up + dash tackle goal. */
public class ChargeTackleBehavior implements CompanionBehavior {
    private final int chargeTicks;
    private final double dashSpeed;
    private final float damage;
    private final double knockback;
    private final int cooldownTicks;

    public ChargeTackleBehavior(int chargeTicks, double dashSpeed, float damage, double knockback, int cooldownTicks) {
        this.chargeTicks = chargeTicks;
        this.dashSpeed = dashSpeed;
        this.damage = damage;
        this.knockback = knockback;
        this.cooldownTicks = cooldownTicks;
    }

    @Override
    public void apply(MobEntity mob, ServerPlayerEntity owner, GoalSelector goals, GoalSelector targets) {
        if (mob instanceof PathAwareEntity paw) {
            goals.add(2, new SharedGoals.ChargeTackleGoal(paw, chargeTicks, dashSpeed, damage, knockback, cooldownTicks));
        }
    }
}
