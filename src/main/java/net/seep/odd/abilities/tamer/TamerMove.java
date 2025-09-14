// net/seep/odd/abilities/tamer/TamerMove.java
package net.seep.odd.abilities.tamer;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;

public record TamerMove(
        String id,
        String name,
        String description,
        Category category,
        int power
) {
    public enum Category { PHYSICAL, SPECIAL, STATUS }

    /** Hook to execute the move (optional, for battle logic later). */
    public interface Executor {
        void use(ServerWorld sw, MobEntity user, LivingEntity target);
    }
}
