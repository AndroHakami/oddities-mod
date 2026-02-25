// FILE: src/main/java/net/seep/odd/abilities/artificer/mixer/brew/AutoColdEffect.java
package net.seep.odd.abilities.artificer.mixer.brew;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.UUID;

public final class AutoColdEffect {
    private AutoColdEffect() {}

    private static boolean inited = false;
    private static final Object2LongOpenHashMap<UUID> END = new Object2LongOpenHashMap<>();

    private static void initCommon() {
        if (inited) return;
        inited = true;

        ServerTickEvents.START_SERVER_TICK.register(AutoColdEffect::serverTick);

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            END.removeLong(handler.player.getUuid());
        });
    }

    public static void start(ServerPlayerEntity player, int durationTicks) {
        if (player == null || player.getServer() == null) return;
        initCommon();

        long end = player.getServer().getTicks() + durationTicks;
        END.put(player.getUuid(), end);

        // tell nearby clients to render aura around THIS player
        if (player.getWorld() instanceof ServerWorld sw) {
            net.seep.odd.abilities.artificer.mixer.AutoColdNet.sendStart(sw, player.getId(), durationTicks);
        }
    }

    private static void serverTick(MinecraftServer server) {
        long now = server.getTicks();

        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            long end = END.getOrDefault(p.getUuid(), Long.MIN_VALUE);
            if (end == Long.MIN_VALUE) continue;

            if (now >= end) {
                END.removeLong(p.getUuid());
                if (p.getWorld() instanceof ServerWorld sw) {
                    net.seep.odd.abilities.artificer.mixer.AutoColdNet.sendStop(sw, p.getId());
                }
                continue;
            }

            // “double swim speed” feel: Dolphin’s Grace is perfect for this
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.DOLPHINS_GRACE, 10, 0, false, false, true));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.WATER_BREATHING, 10, 0, false, false, true));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 10, 0, false, false, true));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.CONDUIT_POWER, 10, 0, false, false, true));
        }
    }
}
