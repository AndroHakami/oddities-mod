package net.seep.odd.abilities.tamer.ai.behavior;

import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.server.network.ServerPlayerEntity;

public class MeleeVanillaBehavior implements CompanionBehavior {
    private final double speed;
    private final boolean pauseWhenIdle;

    public MeleeVanillaBehavior(double speed, boolean pauseWhenIdle) {
        this.speed = speed;
        this.pauseWhenIdle = pauseWhenIdle;
    }

    @Override
    public void apply(MobEntity mob, ServerPlayerEntity owner, GoalSelector goals, GoalSelector targets) {
        if (!(mob instanceof PathAwareEntity paw)) return;
        var atk = mob.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (atk != null && atk.getBaseValue() < 2.0) atk.setBaseValue(2.0); // gentle bump
        goals.add(2, new MeleeAttackGoal(paw, speed, pauseWhenIdle));
    }
}
