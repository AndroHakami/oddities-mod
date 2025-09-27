package net.seep.odd.abilities.spectral;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

public final class SpectralPhaseHooks {
    private SpectralPhaseHooks() {}

    public static boolean isPhasing(Entity e) {
        if (!(e instanceof PlayerEntity p)) return false;
        if (p.getWorld().isClient()) {
            return SpectralPhaseClientFlag.isPhased(p.getUuid());
        } else {
            return (p instanceof ServerPlayerEntity sp)
                    && net.seep.odd.abilities.power.SpectralPhasePower.isPhased(sp);
        }
    }
}