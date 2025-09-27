package net.seep.odd.abilities.spectral;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Environment(EnvType.CLIENT)
public final class SpectralRenderState {
    private SpectralRenderState() {}
    private static final Set<UUID> PHASED = new HashSet<>();

    public static void setPhased(UUID id, boolean on) {
        if (on) PHASED.add(id); else PHASED.remove(id);


    }

    public static boolean isPhased(LivingEntity e) {
        return PHASED.contains(e.getUuid());
    }

    /** (Optional) keep local player "sticky" too; harmless redundancy with the mixin. */
    public static void installClientTickKeepAlive() {
        ClientTickEvents.END_CLIENT_TICK.register((MinecraftClient client) -> {
            var p = client.player;
            if (p != null && isPhased(p)) {
                p.noClip = true;
                p.fallDistance = 0.0f;
            }
        });
    }
}
