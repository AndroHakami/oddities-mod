package net.seep.odd.abilities.net;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import net.seep.odd.abilities.power.Power;
import net.seep.odd.abilities.power.Powers;
import net.seep.odd.abilities.power.ChargedPower;
import net.seep.odd.abilities.data.ChargeState;

import java.util.UUID;

public final class PowerNetworking {
    private PowerNetworking() {}

    public static final Identifier SYNC     = new Identifier("odd", "power_sync");
    public static final Identifier COOLDOWN = new Identifier("odd", "power_cooldown");
    public static final Identifier CHARGES  = new Identifier("odd", "power_charges"); // NEW

    /** Send current power id to a player (also sends initial charge snapshots if applicable). */
    public static void syncTo(ServerPlayerEntity player, String id) {
        // id sync
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(id);
        ServerPlayNetworking.send(player, SYNC, buf);

        // also push initial charge lanes so HUD has correct numbers immediately
        Power p = Powers.get(id);
        if (p instanceof ChargedPower cp) {
            long now = player.getWorld().getTime();
            UUID u = player.getUuid();
            ChargeState cs = ChargeState.get(player.getServer());
            String[] slots = {"primary", "secondary", "third", "fourth"};
            for (String s : slots) {
                if (!cp.usesCharges(s)) continue;
                int max = Math.max(1, cp.maxCharges(s));
                String key = id + "#" + s;
                // tick + snapshot
                ChargeState.Snapshot snap = cs.snapshot(key, u, max, now);
                sendCharges(player, s, snap.have, snap.max, snap.recharge, snap.nextReady, snap.now);
            }
        }
    }

    /** Send a cooldown window to a client HUD (ticks). */
    public static void sendCooldown(ServerPlayerEntity player, String slot, long ticks) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(slot);
        buf.writeVarInt((int) Math.max(0, ticks));
        ServerPlayNetworking.send(player, COOLDOWN, buf);
    }

    /** Send a charge lane snapshot for a given slot. */
    public static void sendCharges(ServerPlayerEntity player, String slot, int have, int max, long recharge, long nextReady, long serverNow) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(slot);
        buf.writeVarInt(Math.max(0, have));
        buf.writeVarInt(Math.max(1, max));
        buf.writeVarLong(Math.max(0L, recharge));
        buf.writeVarLong(Math.max(0L, nextReady));
        buf.writeVarLong(Math.max(0L, serverNow));
        ServerPlayNetworking.send(player, CHARGES, buf);
    }
}
