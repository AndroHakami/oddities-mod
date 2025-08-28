package net.seep.odd.abilities.net;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
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
    public static final net.minecraft.util.Identifier HUD_SYNC  = new net.minecraft.util.Identifier("odd","tamer_hud_sync");
    public static final net.minecraft.util.Identifier PARTY_KICK = new net.minecraft.util.Identifier("odd","tamer_party_kick");



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
        registerKickServer();
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
    public static void sendHud(net.minecraft.server.network.ServerPlayerEntity p,
                               String displayName, net.minecraft.util.Identifier iconTex,
                               float hp, float maxHp, int level, int exp, int next) {
        var buf = PacketByteBufs.create();
        buf.writeString(displayName);
        buf.writeIdentifier(iconTex);
        buf.writeFloat(hp);
        buf.writeFloat(maxHp);
        buf.writeVarInt(level);
        buf.writeVarInt(exp);
        buf.writeVarInt(next);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, HUD_SYNC, buf);
    }

    // --- Client receiver (call in initClient()) ---
    private static void registerHudClient() {
        ClientPlayNetworking.registerGlobalReceiver(HUD_SYNC, (client, handler, buf, rs) -> {
            String display = buf.readString();
            Identifier icon = buf.readIdentifier();
            float hp = buf.readFloat();
            float maxHp = buf.readFloat();
            int level = buf.readVarInt();
            int exp   = buf.readVarInt();
            int next  = buf.readVarInt();

            client.execute(() ->
                    net.seep.odd.abilities.tamer.client.TamerHudState.update(display, icon, hp, maxHp, level, exp, next)
            );
        });
    }

    // --- Client -> Server: kick party member (index) ---
    public static void sendKickRequest(int index) {
        var buf = PacketByteBufs.create();
        buf.writeVarInt(index);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(PARTY_KICK, buf);
    }

    // --- Server receiver (call in initCommon()) ---
    private static void registerKickServer() {
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(PARTY_KICK,
                (server, player, handler, buf, responseSender) -> {
                    int idx = buf.readVarInt();
                    server.execute(() -> net.seep.odd.abilities.tamer.TamerServerHooks.handleKick(player, idx));
                });
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
        registerHudClient();

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
