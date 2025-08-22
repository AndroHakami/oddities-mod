package net.seep.odd.abilities.net;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.abilities.tamer.PartyMember;

import java.util.List;

public final class TamerNet {
    private TamerNet() {}

    public static final Identifier OPEN_PARTY_S2C     = new Identifier(Oddities.MOD_ID, "tamer_open_party");
    public static final Identifier RENAME_C2S         = new Identifier(Oddities.MOD_ID, "tamer_rename");
    public static final Identifier OPEN_WHEEL_S2C     = new Identifier(Oddities.MOD_ID, "tamer_open_wheel");
    public static final Identifier SUMMON_SELECT_C2S  = new Identifier(Oddities.MOD_ID, "tamer_summon_select");

    /* ===== Common (server) ===== */
    public static void initCommon() {
        ServerPlayNetworking.registerGlobalReceiver(RENAME_C2S, (server, player, handler, buf, resp) -> {
            int index   = buf.readVarInt();
            String name = buf.readString(64);
            server.execute(() ->
                    net.seep.odd.abilities.tamer.TamerServerHooks.handleRename(player, index, name)
            );
        });

        ServerPlayNetworking.registerGlobalReceiver(SUMMON_SELECT_C2S, (server, player, handler, buf, resp) -> {
            int index = buf.readVarInt();
            server.execute(() ->
                    net.seep.odd.abilities.tamer.TamerServerHooks.handleSummonSelect(player, index)
            );
        });
    }

    public static void sendOpenParty(ServerPlayerEntity player, List<PartyMember> party) {
        PacketByteBuf out = new PacketByteBuf(Unpooled.buffer());
        NbtCompound root  = new NbtCompound();
        NbtList arr       = new NbtList();
        for (PartyMember m : party) arr.add(m.toNbt());
        root.put("party", arr);
        out.writeNbt(root);
        ServerPlayNetworking.send(player, OPEN_PARTY_S2C, out);
    }

    public static void sendOpenWheel(ServerPlayerEntity player, List<PartyMember> party) {
        PacketByteBuf out = new PacketByteBuf(Unpooled.buffer());
        NbtCompound root  = new NbtCompound();
        NbtList arr       = new NbtList();
        for (PartyMember m : party) arr.add(m.toNbt());
        root.put("party", arr);
        out.writeNbt(root);
        ServerPlayNetworking.send(player, OPEN_WHEEL_S2C, out);
    }

    /* ===== Client ===== */
    public static void initClient() {
        ClientPlayNetworking.registerGlobalReceiver(OPEN_PARTY_S2C, (client, handler, buf, resp) -> {
            NbtCompound root = buf.readNbt(); if (root == null) return;
            client.execute(() ->
                    net.seep.odd.abilities.tamer.client.TamerScreens.openPartyScreen(root)
            );
        });

        ClientPlayNetworking.registerGlobalReceiver(OPEN_WHEEL_S2C, (client, handler, buf, resp) -> {
            NbtCompound root = buf.readNbt(); if (root == null) return;
            client.execute(() ->
                    net.seep.odd.abilities.tamer.client.TamerScreens.openWheelScreen(root)
            );
        });
    }

    public static void sendRename(int index, String newName) {
        PacketByteBuf out = new PacketByteBuf(Unpooled.buffer());
        out.writeVarInt(index);
        out.writeString(newName == null ? "" : newName, 64);
        ClientPlayNetworking.send(RENAME_C2S, out);
    }

    public static void sendSummonSelect(int index) {
        PacketByteBuf out = new PacketByteBuf(Unpooled.buffer());
        out.writeVarInt(index);
        ClientPlayNetworking.send(SUMMON_SELECT_C2S, out);
    }
}
