// src/main/java/net/seep/odd/mixin/necromancer/TrackTargetGoalAccessor.java
package net.seep.odd.mixin.necromancer;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.ai.goal.TrackTargetGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TrackTargetGoal.class)
public interface TrackTargetGoalAccessor {
    @Accessor("mob")
    MobEntity oddities$getMob();
}
