// src/main/java/net/seep/odd/abilities/tamer/ai/SpeciesGoals.java
package net.seep.odd.abilities.tamer.ai;

import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

import net.seep.odd.entity.ModEntities;
import net.seep.odd.abilities.tamer.ai.behavior.RangeShurikenBehavior;
import net.seep.odd.abilities.tamer.ai.behavior.HeadButterBehavior;

public final class SpeciesGoals {
    private SpeciesGoals() {
    }

    // helper: seconds → ticks
    private static int T(double seconds) {
        return (int) Math.round(seconds * 20.0);
    }

    /**
     * Attach species-specific primary combat goals. Return true if attached.
     */
    public static boolean applyFor(MobEntity mob,
                                   ServerPlayerEntity owner,
                                   GoalSelector goals,
                                   GoalSelector targets) {

        // Villager: ranged shuriken kiting/orbiting
        if (mob instanceof VillagerEntity && mob instanceof PathAwareEntity paw) {
            goals.add(2, new RangeShurikenBehavior(
                    1.25, // move speed
                    4.0,  // min range
                    9.0,  // max range
                    18,   // cooldown ticks
                    3.5   // orbit radius
            ).asGoal(paw, owner));
            return true;
        }

        // Villager Evo: headbutt timed barrage
        if (mob.getType() == ModEntities.VILLAGER_EVO && mob instanceof PathAwareEntity paw2) {
            goals.add(2, new HeadButterBehavior(
                    (int)Math.round(0.96 * 20), // windup
                    (int)Math.round(0.20 * 20), // interval
                    (int)Math.round(4.00 * 20), // total
                    3.5f                        // damage per hit
            ).asGoal(paw2, owner));
            return true;
        }

        return false;
    }
}
