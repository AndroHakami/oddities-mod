package net.seep.odd.abilities.tamer;

import net.minecraft.entity.LivingEntity;

/** XP math helpers. */
public final class TamerXp {
    private TamerXp() {}

    // Quadratic-ish curve: total(n) ~= 15n^2 + 50n
    public static int totalExpForLevel(int level) {
        level = Math.max(1, Math.min(PartyMember.MAX_LEVEL, level));
        return 15 * level * level + 50 * level;
    }

    /** How much XP a kill should grant. Tuned off victim's max health. */
    public static int xpForKill(LivingEntity victim) {
        float hp = Math.max(4f, victim.getMaxHealth());
        // scale a bit with health; clamp a reasonable range
        int base = Math.round(2f + hp * 0.75f);
        if (base < 4) base = 4;
        if (base > 40) base = 40;
        return base;
    }
}
