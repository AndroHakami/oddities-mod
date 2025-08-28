package net.seep.odd.abilities.tamer.ai;

import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.seep.odd.mixin.MobEntityAccessor;

public final class CompanionAI {
    private CompanionAI() {}

    /** Base install + species behaviors. Call this from your summon hook. */
    public static void install(MobEntity mob, ServerPlayerEntity owner) {
        // Access selectors (protected) via accessor
        GoalSelector goals   = ((MobEntityAccessor)(Object)mob).odd$getGoalSelector();
        GoalSelector targets = ((MobEntityAccessor)(Object)mob).odd$getTargetSelector();

        // Clear goals/targets safely
        mob.clearGoals(g -> true);
        for (var e : new java.util.ArrayList<>(targets.getGoals())) {
            targets.remove(e.getGoal());
        }

        // --- Base action goals common to all companions ---
        goals.add(0, new SwimGoal(mob));
        if (mob instanceof PathAwareEntity) {
            goals.add(3, new SharedGoals.FollowOwnerGoalGeneric(mob, owner.getUuid(), 1.20, 2.5, 10.0));
            goals.add(6, new WanderAroundFarGoal((PathAwareEntity)mob, 1.0));
        }
        goals.add(7, new LookAtEntityGoal(mob, ServerPlayerEntity.class, 10.0f));
        goals.add(8, new LookAroundGoal(mob));

        // --- Base target goals ---
        targets.add(1, new SharedGoals.RetaliateIfHurtGoal(mob));
        targets.add(2, new SharedGoals.ProtectOwnerTargetGoal(mob, owner.getUuid()));

        // --- Species-specific combat wiring (behaviors) ---
        SpeciesProfiles.applySpeciesCombat(mob, owner, goals, targets);
    }
}
