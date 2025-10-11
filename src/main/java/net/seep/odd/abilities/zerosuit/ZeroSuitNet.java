package net.seep.odd.abilities.zerosuit;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

import net.seep.odd.Oddities;
import net.seep.odd.abilities.PowerAPI;
import net.seep.odd.abilities.power.Powers;
import net.seep.odd.abilities.power.ZeroSuitPower;
import net.seep.odd.abilities.zerosuit.client.ZeroSuitCpmBridge;

public final class ZeroSuitNet {
    private ZeroSuitNet() {}

    public static final Identifier S2C_HUD  = new Identifier(Oddities.MOD_ID, "zero_suit_hud");
    public static final Identifier S2C_ANIM = new Identifier(Oddities.MOD_ID, "zero_suit_anim");
    public static final Identifier C2S_FIRE = new Identifier(Oddities.MOD_ID, "zero_suit_fire");

    private static boolean INIT_COMMON = false;
    private static boolean INIT_CLIENT = false;

    /* ============================ COMMON ============================ */
    public static void initCommon() {
        if (INIT_COMMON) return;
        INIT_COMMON = true;

        // World tick → run the power tick
        ServerTickEvents.END_WORLD_TICK.register((ServerWorld world) -> {
            for (ServerPlayerEntity p : world.getPlayers()) {
                var pow = Powers.get(PowerAPI.get(p));
                if (pow instanceof ZeroSuitPower) ZeroSuitPower.serverTick(p);
            }
        });

        // C2S: client pressed LMB while charging → fire
        ServerPlayNetworking.registerGlobalReceiver(C2S_FIRE, (server, player, handler, buf, responseSender) ->
                server.execute(() -> ZeroSuitPower.onClientRequestedFire(player)));

        ServerPlayConnectionEvents.DISCONNECT.register((ServerPlayNetworkHandler handler, MinecraftServer server) -> {
            // nothing persistent to clear right now (charge/stance are self-clearing)
        });
    }

    public static void sendHud(ServerPlayerEntity to, boolean active, int charge, int max) {
        PacketByteBuf out = PacketByteBufs.create();
        out.writeBoolean(active);
        out.writeInt(charge);
        out.writeInt(max);
        ServerPlayNetworking.send(to, S2C_HUD, out);
    }

    /** Broadcast a tiny CPM animation key to all tracking players (+ self). */
    public static void broadcastAnim(ServerPlayerEntity src, String key) {
        PacketByteBuf out = PacketByteBufs.create();
        out.writeUuid(src.getUuid());
        out.writeString(key, 32);
        for (ServerPlayerEntity p : PlayerLookup.tracking(src)) {
            ServerPlayNetworking.send(p, S2C_ANIM, out);
        }
        ServerPlayNetworking.send(src, S2C_ANIM, out);
    }

    /* ============================ CLIENT ============================ */
    @Environment(EnvType.CLIENT)
    public static void initClient() {
        if (INIT_CLIENT) return;
        INIT_CLIENT = true;

        // HUD
        ClientPlayNetworking.registerGlobalReceiver(S2C_HUD, (client, handler, buf, sender) -> {
            boolean active = buf.readBoolean();
            int charge = buf.readInt();
            int max = buf.readInt();
            client.execute(() -> net.seep.odd.abilities.power.ZeroSuitPower.ClientHud.onHud(active, charge, max));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                net.seep.odd.abilities.power.ZeroSuitPower.ClientHud.onHud(false, 0, 1));

        // CPM anims
        ClientPlayNetworking.registerGlobalReceiver(S2C_ANIM, (client, handler, buf, sender) -> {
            final java.util.UUID who = buf.readUuid();
            final String key = buf.readString(32);
            client.execute(() -> {
                switch (key) {
                    case "stance_on"   -> ZeroSuitCpmBridge.playStance();
                    case "stance_off"  -> ZeroSuitCpmBridge.stopStance();
                    case "charge_on"   -> ZeroSuitCpmBridge.playBlastCharge();
                    case "charge_off"  -> ZeroSuitCpmBridge.stopBlastCharge();
                    case "force_push"  -> ZeroSuitCpmBridge.playForcePush();
                    case "force_pull"  -> ZeroSuitCpmBridge.playForcePull();
                    case "blast_fire"  -> ZeroSuitCpmBridge.playBlastFire();
                    default -> {}
                }
            });
        });

        // While charging, a *left-click edge* should fire the beam even if you aren't hitting anything
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null) return;
            if (!net.seep.odd.abilities.power.ZeroSuitPower.ClientHud.isCharging()) return;

            boolean edge = net.seep.odd.abilities.power.ZeroSuitPower.ClientHud.consumeAttackEdge();
            if (edge) {
                ClientPlayNetworking.send(C2S_FIRE, PacketByteBufs.create());
            }
        });
    }
}
