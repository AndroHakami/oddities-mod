// src/main/java/net/seep/odd/block/falseflower/spell/FalseFlowerSpellEffects.java
package net.seep.odd.block.falseflower.spell;

import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.seep.odd.abilities.fairy.FairySpell;
import net.seep.odd.block.falseflower.FalseFlowerBlockEntity;

import java.util.EnumMap;

public final class FalseFlowerSpellEffects {
    private FalseFlowerSpellEffects() {}

    private static final EnumMap<FairySpell, FalseFlowerSpellEffect> MAP = new EnumMap<>(FairySpell.class);

    static {
        MAP.put(FairySpell.AURA_LEVITATION, new LevitationEffect());
        MAP.put(FairySpell.AURA_HEAVY,      new HeavyEffect());
        MAP.put(FairySpell.AURA_REGEN,      new RegenEffect());
        MAP.put(FairySpell.CROP_GROWTH,     new GrowthEffect());
        MAP.put(FairySpell.BLACKHOLE,       new BlackholeEffect());
        MAP.put(FairySpell.BUBBLE,          new BubbleEffect());

        MAP.put(FairySpell.AREA_MINE,       new MineHasteEffect());
        MAP.put(FairySpell.STONE_PRISON,    new StonePrisonEffect());

        MAP.put(FairySpell.RECHARGE,        (w,pos,state,be,r,box) -> {});
        MAP.put(FairySpell.NONE,            (w,pos,state,be,r,box) -> {});
    }

    public static void tick(FairySpell spell, ServerWorld w, BlockPos pos, BlockState state,
                            FalseFlowerBlockEntity be, int radius, Box box) {
        FalseFlowerSpellEffect fx = MAP.get(spell);
        if (fx != null) fx.tick(w, pos, state, be, radius, box);
    }
}
