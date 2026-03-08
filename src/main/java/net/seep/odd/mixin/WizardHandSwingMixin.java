package net.seep.odd.mixin;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.seep.odd.abilities.PowerAPI;
import net.seep.odd.abilities.power.Powers;
import net.seep.odd.abilities.power.WizardPower;
import net.seep.odd.abilities.wizard.WizardCasting;
import net.seep.odd.abilities.wizard.WizardSwingSuppress;
import net.seep.odd.item.ModItems;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class WizardHandSwingMixin {

    @Shadow public ServerPlayerEntity player;

    @Unique private static final Object2LongOpenHashMap<UUID> LAST_SWING_TICK = new Object2LongOpenHashMap<>();
    @Unique private static final Object2LongOpenHashMap<UUID> PENDING_CAST_TICK = new Object2LongOpenHashMap<>();
    @Unique private static boolean HOOKED = false;

    @Unique
    private static void ensureHook() {
        if (HOOKED) return;
        HOOKED = true;
        ServerTickEvents.END_SERVER_TICK.register(WizardHandSwingMixin::flushPending);
    }

    @Unique
    private static void flushPending(MinecraftServer server) {
        if (PENDING_CAST_TICK.isEmpty()) return;

        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            UUID id = p.getUuid();
            long due = PENDING_CAST_TICK.getOrDefault(id, Long.MIN_VALUE);
            if (due == Long.MIN_VALUE) continue;

            long now = p.getWorld().getTime();
            if (due > now) continue;

            // remove first to prevent double-run
            PENDING_CAST_TICK.removeLong(id);

            // ✅ if a combo was just confirmed, skip the normal cast
            if (WizardSwingSuppress.isBlockedAt(p, due)) continue;

            // re-check conditions
            if (!p.getMainHandStack().isOf(ModItems.WALKING_STICK)) continue;
            if (!(Powers.get(PowerAPI.get(p)) instanceof WizardPower)) continue;

            WizardCasting.castNormal(p);
        }
    }

    @Inject(method = "onHandSwing", at = @At("HEAD"))
    private void odd$wizardCastOnSwing(HandSwingC2SPacket packet, CallbackInfo ci) {
        if (player == null) return;

        ensureHook();

        // only main hand swings
        if (packet.getHand() != Hand.MAIN_HAND) return;

        // must be holding the staff
        if (!player.getMainHandStack().isOf(ModItems.WALKING_STICK)) return;

        // must be wizard power
        if (!(Powers.get(PowerAPI.get(player)) instanceof WizardPower)) return;

        long now = player.getWorld().getTime();
        UUID id = player.getUuid();
        long last = LAST_SWING_TICK.getOrDefault(id, Long.MIN_VALUE);

        // prevent double triggers same tick
        if (now == last) return;
        LAST_SWING_TICK.put(id, now);

        // ✅ delay the normal cast slightly so combo-confirm can arrive and block it
        PENDING_CAST_TICK.put(id, now + 1);
    }
}