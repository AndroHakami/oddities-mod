package net.seep.odd.abilities.net;

import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.annotation.Nullable;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.seep.odd.abilities.tamer.PartyMember;
import net.seep.odd.abilities.tamer.client.TamerScreens;

import java.util.List;

/** Networking for the Tamer power. */
public final class TamerNet {
    private TamerNet() {}

    // Server → Client
    public static final Identifier OPEN_PARTY_S2C   = new Identifier("odd", "tamer_open_party");
    public static final Identifier OPEN_WHEEL_S2C   = new Identifier("odd", "tamer_open_wheel");
    public static final Identifier OPEN_COMMAND_S2C = new Identifier("odd", "tamer_open_command"); // NEW
    public static final Identifier HUD_SYNC         = new Identifier("odd", "tamer_hud_sync");

    // Client → Server
    public static final Identifier PARTY_RENAME   = new Identifier("odd", "tamer_party_rename");
    public static final Identifier PARTY_KICK     = new Identifier("odd", "tamer_party_kick");
    public static final Identifier SUMMON_SELECT  = new Identifier("odd", "tamer_summon_select");
    public static final Identifier PARTY_HEAL     = new Identifier("odd", "tamer_party_heal");
    public static final Identifier MODE_SET_C2S   = new Identifier("odd", "tamer_mode_set"); // NEW
    public static final Identifier RECALL_C2S     = new Identifier("odd", "tamer_recall");    // NEW

    /* -------------------- Init -------------------- */

    /** Call once from your *common* init. */
    public static void initCommon() {
        registerRenameServer();
        registerKickServer();
        registerSummonServer();
        registerHealServer();
        registerModeServer();   // NEW
        registerRecallServer(); // NEW
    }

    /** Call once from your *client* init. */
    public static void initClient() {
        registerOpenPartyClient();
        registerOpenWheelClient();
        registerOpenCommandClient(); // NEW
        registerHudClient();
    }

    /* -------------------- Client → Server senders -------------------- */

    public static void sendRename(int index, String newName) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(index);
        buf.writeString(newName);
        ClientPlayNetworking.send(PARTY_RENAME, buf);
    }

    public static void sendKickRequest(int index) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(index);
        ClientPlayNetworking.send(PARTY_KICK, buf);
    }

    public static void sendSummonSelect(int index) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(index);
        ClientPlayNetworking.send(SUMMON_SELECT, buf);
    }

    public static void sendHeal(int index) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(index);
        ClientPlayNetworking.send(PARTY_HEAL, buf);
    }

    /** NEW: set companion mode (0=PASSIVE,1=FOLLOW,2=AGGRESSIVE) */
    public static void sendModeSet(int ordinal) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(ordinal);
        ClientPlayNetworking.send(MODE_SET_C2S, buf);
    }

    /** NEW: recall active companion (despawn + clear active) */
    public static void sendRecall() {
        ClientPlayNetworking.send(RECALL_C2S, PacketByteBufs.empty());
    }

    /* -------------------- Server → Client senders -------------------- */

    /** Top-right HUD sync (name/icon/hp/xp). */
    public static void sendHud(ServerPlayerEntity p,
                               String displayName,
                               @Nullable Identifier iconTex,
                               float hp, float maxHp,
                               int level, int exp, int next) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(displayName);

        // Fallback to your built-in default icon if null
        Identifier safeIcon = (iconTex != null)
                ? iconTex
                : new Identifier("odd", "textures/gui/tamer/icons/default.png");
        buf.writeIdentifier(safeIcon);

        buf.writeFloat(hp);
        buf.writeFloat(maxHp);
        buf.writeVarInt(level);
        buf.writeVarInt(exp);
        buf.writeVarInt(next);

        ServerPlayNetworking.send(p, HUD_SYNC, buf);
    }

    /** Open/refresh the Party Manager with a party snapshot. */
    public static void sendOpenParty(ServerPlayerEntity player, List<PartyMember> party) {
        PacketByteBuf out = PacketByteBufs.create();
        NbtCompound root = new NbtCompound();
        NbtList arr = new NbtList();
        for (PartyMember m : party) arr.add(m.toNbt());
        root.put("party", arr);
        out.writeNbt(root);
        ServerPlayNetworking.send(player, OPEN_PARTY_S2C, out);
    }

    /** Open/refresh the radial Summon chooser. */
    public static void sendOpenWheel(ServerPlayerEntity player, List<PartyMember> party) {
        PacketByteBuf out = PacketByteBufs.create();
        NbtCompound root = new NbtCompound();
        NbtList arr = new NbtList();
        for (PartyMember m : party) arr.add(m.toNbt());
        root.put("party", arr);
        out.writeNbt(root);
        ServerPlayNetworking.send(player, OPEN_WHEEL_S2C, out);
    }

    /** NEW: open compact Command wheel near crosshair. */
    public static void sendOpenCommand(ServerPlayerEntity player, boolean hasActive, int currentModeOrdinal) {
        PacketByteBuf out = PacketByteBufs.create();
        out.writeBoolean(hasActive);
        out.writeVarInt(currentModeOrdinal);
        ServerPlayNetworking.send(player, OPEN_COMMAND_S2C, out);
    }

    /* -------------------- Server receivers -------------------- */

    private static void registerRenameServer() {
        ServerPlayNetworking.registerGlobalReceiver(PARTY_RENAME, (server, player, handler, buf, rs) -> {
            int idx = buf.readVarInt();
            String name = buf.readString(64);
            server.execute(() -> net.seep.odd.abilities.tamer.TamerServerHooks.handleRename(player, idx, name));
        });
    }

    private static void registerKickServer() {
        ServerPlayNetworking.registerGlobalReceiver(PARTY_KICK, (server, player, handler, buf, rs) -> {
            int idx = buf.readVarInt();
            server.execute(() -> net.seep.odd.abilities.tamer.TamerServerHooks.handleKick(player, idx));
        });
    }

    private static void registerSummonServer() {
        ServerPlayNetworking.registerGlobalReceiver(SUMMON_SELECT, (server, player, handler, buf, rs) -> {
            int idx = buf.readVarInt();
            server.execute(() -> net.seep.odd.abilities.tamer.TamerServerHooks.handleSummonSelect(player, idx));
        });
    }

    private static void registerHealServer() {
        ServerPlayNetworking.registerGlobalReceiver(PARTY_HEAL, (server, player, handler, buf, rs) -> {
            int idx = buf.readVarInt();
            server.execute(() -> net.seep.odd.abilities.tamer.TamerServerHooks.handleHeal(player, idx));
        });
    }

    private static void registerModeServer() {
        ServerPlayNetworking.registerGlobalReceiver(MODE_SET_C2S, (server, player, handler, buf, rs) -> {
            int ord = buf.readVarInt();
            server.execute(() -> net.seep.odd.abilities.tamer.TamerServerHooks.handleModeChange(player, ord));
        });
    }

    private static void registerRecallServer() {
        ServerPlayNetworking.registerGlobalReceiver(RECALL_C2S, (server, player, handler, buf, rs) ->
                server.execute(() -> net.seep.odd.abilities.tamer.TamerServerHooks.handleRecall(player)));
    }

    /* -------------------- Client receivers -------------------- */

    private static void registerOpenPartyClient() {
        ClientPlayNetworking.registerGlobalReceiver(OPEN_PARTY_S2C, (client, handler, buf, rs) -> {
            NbtCompound root = buf.readNbt();
            if (root == null) return;
            client.execute(() -> TamerScreens.openPartyScreen(root));
        });
    }

    private static void registerOpenWheelClient() {
        ClientPlayNetworking.registerGlobalReceiver(OPEN_WHEEL_S2C, (client, handler, buf, rs) -> {
            NbtCompound root = buf.readNbt();
            if (root == null) return;
            client.execute(() -> net.seep.odd.abilities.tamer.client.SummonWheelHud.openFromNbt(root));
        });
    }

    /** NEW: open the compact command HUD radial. */
    private static void registerOpenCommandClient() {
        ClientPlayNetworking.registerGlobalReceiver(OPEN_COMMAND_S2C, (client, handler, buf, rs) -> {
            boolean hasActive = buf.readBoolean();
            int modeOrdinal   = buf.readVarInt();
            client.execute(() -> net.seep.odd.abilities.tamer.client.CommandWheelHud.open(hasActive, modeOrdinal));
        });
    }

    private static void registerHudClient() {
        ClientPlayNetworking.registerGlobalReceiver(HUD_SYNC, (client, handler, buf, rs) -> {
            String name = buf.readString(128);
            Identifier icon = buf.readIdentifier();
            float hp = buf.readFloat();
            float maxHp = buf.readFloat();
            int level = buf.readVarInt();
            int exp = buf.readVarInt();
            int next = buf.readVarInt();
            client.execute(() ->
                    net.seep.odd.abilities.tamer.client.TamerHudState.update(
                            name, icon, hp, maxHp, level, exp, next
                    )
            );
        });
    }
}
