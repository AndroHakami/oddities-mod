// src/main/java/net/seep/odd/abilities/conquer/client/CorruptionRenderUtil.java
package net.seep.odd.abilities.conquer.client;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.seep.odd.status.ModStatusEffects;

public final class CorruptionRenderUtil {
    private CorruptionRenderUtil() {}

    private static boolean printedOnce = false;

    public static boolean hasCorruption(LivingEntity entity) {
        // derive the real ID from the registry (no guessing)
        Identifier corruptionId = Registries.STATUS_EFFECT.getId(ModStatusEffects.CORRUPTION);

        boolean found = false;

        for (StatusEffectInstance inst : entity.getStatusEffects()) {
            Identifier id = Registries.STATUS_EFFECT.getId(inst.getEffectType());
            if (id == null) continue;

            if (!printedOnce) {
                printedOnce = true;
                System.out.println("[Oddities][Conquer] Expected corruption id = " + corruptionId);
                System.out.println("[Oddities][Conquer] Client sees effect on entity = " + id);
            }

            if (corruptionId != null && corruptionId.equals(id)) {
                found = true;
            }
        }

        return found;
    }
}
