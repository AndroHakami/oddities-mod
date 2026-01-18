package net.seep.odd.abilities.conquer.client;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public final class ConquerCorruptionClient {
    private ConquerCorruptionClient() {}

    // IMPORTANT: this matches ANY odd:*corrupt* to avoid ID mismatch pain
    public static boolean isCorrupted(LivingEntity e) {
        for (StatusEffectInstance inst : e.getStatusEffects()) {
            Identifier id = Registries.STATUS_EFFECT.getId(inst.getEffectType());
            if (id == null) continue;
            if ("odd".equals(id.getNamespace()) && id.getPath().toLowerCase().contains("corrupt")) {
                return true;
            }
        }
        return false;
    }
}
