// FILE: net/seep/odd/abilities/icewitch/IceWitchInit.java
package net.seep.odd.abilities.icewitch;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import net.seep.odd.abilities.PowerAPI;
import net.seep.odd.abilities.power.IceWitchPower;
import net.seep.odd.abilities.power.Powers;
import net.seep.odd.abilities.icewitch.client.IceSpellAreaRenderer;
import net.seep.odd.abilities.icewitch.client.IceWitchHud;
import net.seep.odd.entity.ModEntities;

public final class IceWitchInit {
    private IceWitchInit(){}

    public static void registerCommon() {
        // Packets
        IceWitchPackets.registerServer();

        // Per-player ticking
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                if (Powers.get(PowerAPI.get(p)) instanceof IceWitchPower) {
                    IceWitchPower.serverTick(p);
                }
            }
        });

        // Optional: melee freezing hook
        AttackEntityCallback.EVENT.register((player, world, hand, target, hit) -> {
            if (!(player instanceof ServerPlayerEntity sp)) return net.minecraft.util.ActionResult.PASS;
            if (Powers.get(PowerAPI.get(sp)) instanceof IceWitchPower && target instanceof net.minecraft.entity.LivingEntity le) {
                IceWitchPower.onMeleeFreeze(sp, le);
            }
            return net.minecraft.util.ActionResult.PASS;
        });

        // Client-only bits
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            IceWitchPackets.registerClient();
            if (ModEntities.ICE_SPELL_AREA != null) {
                EntityRendererRegistry.register(ModEntities.ICE_SPELL_AREA, IceSpellAreaRenderer::new);
            }
            IceWitchHud.register();
        }
    }
}
