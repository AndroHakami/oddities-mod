package net.seep.odd.abilities.tamer;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.seep.odd.abilities.tamer.ai.CompanionAI;

public final class TamerAI {
    private TamerAI() {}
    public static void install(MobEntity mob, ServerPlayerEntity owner) {
        CompanionAI.install(mob, owner);
    }
}
