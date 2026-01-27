// src/main/java/net/seep/odd/block/falseflower/spell/FalseFlowerSpellRegistry.java
package net.seep.odd.block.falseflower.spell;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.seep.odd.abilities.fairy.FairySpell;

public final class FalseFlowerSpellRegistry {
    private FalseFlowerSpellRegistry() {}

    private static final Object2ObjectOpenHashMap<FairySpell, FalseFlowerSpellDef> DEF = new Object2ObjectOpenHashMap<>();

    // one-shot arming defaults
    private static final int ONE_SHOT_BASE = 18;   // ~0.9s at POWER=1
    private static final int ONE_SHOT_PER  = 10;   // +0.5s each power step (POWER 2/3 slower)

    static {
        // AURAS (activation timing ignored)
        reg(FairySpell.AURA_LEVITATION, new LevitationEffect(), 0.06f, false, 0, 0);
        reg(FairySpell.AURA_HEAVY,      new HeavyEffect(),      0.08f, false, 0, 0);
        reg(FairySpell.AURA_REGEN,      new RegenEffect(),      0.06f, false, 0, 0);
        reg(FairySpell.BLACKHOLE,       new BlackholeEffect(),  0.12f, false, 0, 0);
        reg(FairySpell.BUBBLE,          new BubbleEffect(),     0.12f, false, 0, 0);

        reg(FairySpell.AREA_MINE,       new MineHasteEffect(),  0.08f, false, 0, 0);
        reg(FairySpell.CROP_GROWTH,     new GrowthEffect(),     0.08f, false, 0, 0);

        reg(FairySpell.MAGIC_BULLETS,   new MagicBulletsEffect(),   0.10f, false, 0, 0);
        reg(FairySpell.WATER_BREATHING, new WaterBreathingEffect(), 0.06f, false, 0, 0);
        reg(FairySpell.TINY_WORLD,      new TinyWorldEffect(),      0.10f, false, 0, 0);
        reg(FairySpell.SWITCHERANO,     new SwitcheranoEffect(),    0.10f, false, 0, 0);
        reg(FairySpell.WEAKNESS,        new WeaknessEffect(),       0.08f, false, 0, 0);

        // ONE-SHOTS (arm → fire once → off)
        reg(FairySpell.STONE_PRISON, new StonePrisonEffect(), 0.0f, true, ONE_SHOT_BASE, ONE_SHOT_PER);
        reg(FairySpell.STORM,        new StormEffect(),       0.0f, true, ONE_SHOT_BASE, ONE_SHOT_PER);
        reg(FairySpell.LIFT_OFF,     new LiftOffEffect(),     0.0f, true, ONE_SHOT_BASE, ONE_SHOT_PER);
        reg(FairySpell.BANISH,       new BanishEffect(),      0.0f, true, ONE_SHOT_BASE + 6, ONE_SHOT_PER + 2); // feels a bit "bigger"
        reg(FairySpell.EXTINGUISH,   new ExtinguishEffect(),  0.0f, true, ONE_SHOT_BASE, ONE_SHOT_PER);
        reg(FairySpell.RETURN_POINT, new ReturnPointEffect(), 0.0f, true, ONE_SHOT_BASE, ONE_SHOT_PER);

        // RECHARGE handled by CastLogic, NONE does nothing.
    }

    private static void reg(FairySpell s, FalseFlowerSpellEffect fx, float baseDrain, boolean oneShot, int baseArm, int perPower) {
        DEF.put(s, new FalseFlowerSpellDef(fx, baseDrain, oneShot, baseArm, perPower));
    }

    public static FalseFlowerSpellDef get(FairySpell spell) {
        return (spell == null) ? null : DEF.get(spell);
    }
}
