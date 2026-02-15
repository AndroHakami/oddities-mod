// FILE: src/main/java/net/seep/odd/mixin/WizardHandSwingMixin.java
package net.seep.odd.mixin;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.seep.odd.abilities.PowerAPI;
import net.seep.odd.abilities.power.Powers;
import net.seep.odd.abilities.power.WizardPower;
import net.seep.odd.abilities.wizard.WizardCasting;
import net.seep.odd.item.ModItems;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class WizardHandSwingMixin {

    @Shadow public ServerPlayerEntity player;

    // tiny anti-double-fire safety (same tick spam protection)
    private static final Object2LongOpenHashMap<UUID> LAST_SWING_TICK = new Object2LongOpenHashMap<>();

    @Inject(method = "onHandSwing", at = @At("HEAD"))
    private void odd$wizardCastOnSwing(HandSwingC2SPacket packet, CallbackInfo ci) {
        if (player == null) return;

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

        // ✅ CAST ALWAYS ON SWING (AIR OR HIT)


        WizardCasting.castNormal(player);
    }
}
