// net/seep/odd/abilities/tamer/TamerStats.java
package net.seep.odd.abilities.tamer;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;

/** Centralized stat scaling. Uses species baselines from PartyMember. */
public final class TamerStats {
    private TamerStats() {}

    /* Tunables */
    private static final double HP_PER_LEVEL  = 1.0;    // +HP per level
    private static final double ATK_PER_LEVEL = 0.25;   // +ATK per level
    private static final double SPD_PER_LEVEL = 0.004;  // +Speed per level
    private static final double DEF_PER_LEVEL = 0.15;   // +Armor per level
    private static final double SPD_CAP       = 0.45;   // speed ceiling
    private static final double ATK_FLOOR     = 2.0;    // minimum useful attack
    private static final double HP_FLOOR      = 20.0;   // only floor if species < 20

    public record Scaled(double maxHp, double attack, double speed, double defense) {}

    /** Compute scaled values for current level using saved baselines. */
    private static Scaled computeScaled(LivingEntity le, PartyMember pm) {
        EntityAttributeInstance hpI  = le.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        EntityAttributeInstance atkI = le.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        EntityAttributeInstance spdI = le.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        EntityAttributeInstance defI = le.getAttributeInstance(EntityAttributes.GENERIC_ARMOR);

        // Baselines (capture if missing)
        double bHp  = pm.baseMaxH > 0 ? pm.baseMaxH : (hpI  != null ? hpI.getBaseValue()  : 20.0);
        double bAtk = pm.baseAtk  > 0 ? pm.baseAtk  : (atkI != null ? atkI.getBaseValue() : ATK_FLOOR);
        double bSpd = pm.baseSpd  > 0 ? pm.baseSpd  : (spdI != null ? spdI.getBaseValue() : 0.1);
        double bDef = pm.baseDef  > 0 ? pm.baseDef  : (defI != null ? defI.getBaseValue() : 0.0);

        if (pm.baseMaxH <= 0 && hpI  != null) pm.baseMaxH = (float)bHp;
        if (pm.baseAtk  <= 0 && atkI != null) pm.baseAtk  = bAtk;
        if (pm.baseSpd  <= 0 && spdI != null) pm.baseSpd  = bSpd;
        if (pm.baseDef  <= 0 && defI != null) pm.baseDef  = bDef;

        // Apply HP floor only when species base is under 20
        bHp = Math.max(bHp, HP_FLOOR);

        int lvl = Math.max(1, pm.level);
        double newHp  = bHp  + (lvl - 1) * HP_PER_LEVEL;
        double newAtk = Math.max(ATK_FLOOR, bAtk + (lvl - 1) * ATK_PER_LEVEL);
        double newSpd = Math.min(SPD_CAP, bSpd + (lvl - 1) * SPD_PER_LEVEL);
        double newDef = Math.max(0.0, bDef + (lvl - 1) * DEF_PER_LEVEL);

        return new Scaled(newHp, newAtk, newSpd, newDef);
    }

    /** Apply on spawn: compute level-scaled bases from PM baselines. */
    public static Scaled applyOnSpawn(LivingEntity le, PartyMember pm) {
        Scaled s = computeScaled(le, pm);
        setIfPresent(le.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH),   s.maxHp());
        setIfPresent(le.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE),s.attack());
        setIfPresent(le.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED),s.speed());
        setIfPresent(le.getAttributeInstance(EntityAttributes.GENERIC_ARMOR),        s.defense());
        return s;
    }

    /** Apply on level up while active (same as spawn). */
    public static Scaled applyOnLevelUp(LivingEntity le, PartyMember pm) {
        return applyOnSpawn(le, pm);
    }

    /** After evolution, reuse same baselines; you can reset if desired. */
    public static Scaled applyAfterEvolution(LivingEntity le, PartyMember pm) {
        return applyOnSpawn(le, pm);
    }

    private static void setIfPresent(EntityAttributeInstance inst, double base) {
        if (inst != null) inst.setBaseValue(base);
    }
}
