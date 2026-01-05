package net.seep.odd.abilities.lunar.net;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.seep.odd.Oddities;
import net.seep.odd.abilities.lunar.item.LunarDrillItem;

/** Packets for Lunar power/drill. */
public final class LunarPackets {
    private LunarPackets() {}

    /* -------------------- IDs -------------------- */

    /** Client → Server: save pattern mask (now 5×5 in lowest bits) + depth to the held drill. */
    public static final Identifier C2S_SET_PATTERN  = new Identifier(Oddities.MOD_ID, "lunar/pattern_set");

    /** Server → Client: play the drill animation on the client’s held drill. */
    public static final Identifier S2C_DRILL_ANIM   = new Identifier(Oddities.MOD_ID, "lunar/drill_anim");

    /** Server → Client: set/clear anchor visuals for this player only. */
    public static final Identifier S2C_ANCHOR_SET_POS    = new Identifier(Oddities.MOD_ID, "lunar/anchor_set_pos");
    public static final Identifier S2C_ANCHOR_SET_ENTITY = new Identifier(Oddities.MOD_ID, "lunar/anchor_set_entity");
    public static final Identifier S2C_ANCHOR_CLEAR      = new Identifier(Oddities.MOD_ID, "lunar/anchor_clear");

    /** Server → Client: tether energy + state sync. */
    public static final Identifier S2C_TETHER_SYNC = new Identifier(Oddities.MOD_ID, "lunar/tether_sync");

    /* -------------------- Registration -------------------- */

    /** Call from common init. */
    public static void registerServerReceivers() {
        // Save the mask + optional depth from the pattern screen into the held Lunar Drill.
        // Backwards compatible: if client only sends a long, depth defaults to 1.
        ServerPlayNetworking.registerGlobalReceiver(C2S_SET_PATTERN, (server, player, handler, buf, resp) -> {
            final long mask = buf.readLong();

            // NEW (optional): depth + hand id (so screen can edit offhand too)
            int depth = 1;
            int handId; // -1 means "auto"
            if (buf.isReadable()) depth = buf.readVarInt();
            if (buf.isReadable()) handId = buf.readVarInt();
            else {
                handId = -1;
            }

            final int fDepth = LunarDrillItem.clampDepth(depth);
            final long fMask  = LunarDrillItem.normalizeMask(mask);

            server.execute(() -> {
                ItemStack stack;

                // If a hand id is provided, prefer that hand.
                if (handId == Hand.MAIN_HAND.ordinal()) stack = player.getMainHandStack();
                else if (handId == Hand.OFF_HAND.ordinal()) stack = player.getOffHandStack();
                else {
                    // old behavior: find any held drill
                    stack = player.getMainHandStack();
                    if (!(stack.getItem() instanceof LunarDrillItem)) stack = player.getOffHandStack();
                }

                if (stack.getItem() instanceof LunarDrillItem) {
                    var nbt = stack.getOrCreateNbt();
                    nbt.putLong(LunarDrillItem.NBT_PATTERN, fMask);
                    nbt.putInt(LunarDrillItem.NBT_DEPTH, fDepth); // NEW
                    player.sendMessage(Text.literal("Saved drill pattern."), true);
                }
            });
        });
    }

    /** Call from CLIENT init (e.g., OdditiesClient). */
    @Environment(EnvType.CLIENT)
    public static void registerClientReceivers() {

        ClientPlayNetworking.registerGlobalReceiver(S2C_ANCHOR_SET_POS, (client, handler, buf, resp) -> {
            BlockPos pos = buf.readBlockPos();
            client.execute(() -> net.seep.odd.abilities.lunar.client.MoonAnchorClient.set(pos));
        });
        ClientPlayNetworking.registerGlobalReceiver(S2C_ANCHOR_SET_ENTITY, (client, handler, buf, resp) -> {
            int id = buf.readVarInt();
            client.execute(() -> net.seep.odd.abilities.lunar.client.MoonAnchorClient.setEntity(id));
        });
        ClientPlayNetworking.registerGlobalReceiver(S2C_ANCHOR_CLEAR, (client, handler, buf, resp) ->
                client.execute(() -> net.seep.odd.abilities.lunar.client.MoonAnchorClient.clear()));

        ClientPlayNetworking.registerGlobalReceiver(S2C_TETHER_SYNC, (client, handler, buf, resp) -> {
            float energy = buf.readFloat();
            float max    = buf.readFloat();
            boolean on   = buf.readBoolean();
            client.execute(() -> net.seep.odd.abilities.power.LunarPower.Hud.setClientTether(energy, max, on));
        });
    }

    /* -------------------- Helpers (server send) -------------------- */

    /** Play the “drill” animation on the client’s held drill. */
    public static void clientPlayDrillAnimation(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, S2C_DRILL_ANIM, PacketByteBufs.create());
    }

    public static void sendAnchorPos(ServerPlayerEntity to, BlockPos pos) {
        PacketByteBuf out = PacketByteBufs.create();
        out.writeBlockPos(pos);
        ServerPlayNetworking.send(to, S2C_ANCHOR_SET_POS, out);
    }

    public static void sendAnchorEntity(ServerPlayerEntity to, int entityId) {
        PacketByteBuf out = PacketByteBufs.create();
        out.writeVarInt(entityId);
        ServerPlayNetworking.send(to, S2C_ANCHOR_SET_ENTITY, out);
    }

    public static void clearAnchor(ServerPlayerEntity to) {
        ServerPlayNetworking.send(to, S2C_ANCHOR_CLEAR, PacketByteBufs.create());
    }

    /** Sync tether charge + on/off flag (throttled by server). */
    public static void syncTether(ServerPlayerEntity to, float energy, float max, boolean on) {
        PacketByteBuf out = PacketByteBufs.create();
        out.writeFloat(energy);
        out.writeFloat(max);
        out.writeBoolean(on);
        ServerPlayNetworking.send(to, S2C_TETHER_SYNC, out);
    }
}
