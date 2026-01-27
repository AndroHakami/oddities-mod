// src/main/java/net/seep/odd/block/falseflower/spell/FalseFlowerSpellDef.java
package net.seep.odd.block.falseflower.spell;

/**
 * Spell definition for a False Flower spell.
 *
 * @param effect              actual server-side effect logic
 * @param baseDrainPerTick    base drain at POWER=1; flower multiplies by power^2
 * @param oneShot             if true, the spell "arms" then fires once and turns off
 * @param activationBaseTicks base arming time in ticks at POWER=1 (only used for oneShot)
 * @param activationPerPower  extra ticks per POWER step (only used for oneShot)
 */
public record FalseFlowerSpellDef(
        FalseFlowerSpellEffect effect,
        float baseDrainPerTick,
        boolean oneShot,
        int activationBaseTicks,
        int activationPerPower
) {}
