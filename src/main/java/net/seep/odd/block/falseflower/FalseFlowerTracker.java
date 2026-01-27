// src/main/java/net/seep/odd/block/falseflower/FalseFlowerTracker.java
package net.seep.odd.block.falseflower;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.seep.odd.Oddities;
import net.seep.odd.abilities.fairy.FairySpell;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class FalseFlowerTracker {
    private FalseFlowerTracker() {}

    public static final Identifier C2S_REQ_SNAPSHOT = id("fairy/flowers/request");
    public static final Identifier S2C_SNAPSHOT     = id("fairy/flowers/snapshot");
    public static final Identifier C2S_TOGGLE       = id("fairy/flowers/toggle");
    public static final Identifier C2S_POWER        = id("fairy/flowers/power");
    public static final Identifier C2S_RENAME       = id("fairy/flowers/rename");
    public static final Identifier C2S_CLEANSE      = id("fairy/flowers/cleanse");

    private static Identifier id(String path) { return new Identifier(Oddities.MOD_ID, path); }

    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);
    private static final Int2ObjectOpenHashMap<Entry> ENTRIES = new Int2ObjectOpenHashMap<>();

    // ✅ keeps visuals updating even when no GUI is open
    private static final int SNAPSHOT_BROADCAST_INTERVAL_TICKS = 2; // ~0.1s
    private static long lastBroadcastTick = Long.MIN_VALUE;

    public static void hook(FalseFlowerBlockEntity be) {
        if (!(be.getWorld() instanceof ServerWorld sw)) return;
        if (be.trackerId == 0) be.trackerId = NEXT_ID.getAndIncrement();
        ENTRIES.put(be.trackerId, new Entry(be.trackerId, sw, be.getPos()));
    }

    public static void unhook(FalseFlowerBlockEntity be) {
        if (be.trackerId != 0) ENTRIES.remove(be.trackerId);
    }

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(C2S_REQ_SNAPSHOT, (server, player, handler, buf, resp) ->
                server.execute(() -> sendSnapshot(player)));

        ServerPlayNetworking.registerGlobalReceiver(C2S_TOGGLE, (server, player, handler, buf, resp) -> {
            int id = buf.readVarInt();
            boolean active = buf.readBoolean();
            server.execute(() -> {
                Entry e = ENTRIES.get(id);
                if (e == null) return;
                FalseFlowerBlockEntity be = e.get();
                if (be != null) be.setActive(active);
                sendSnapshot(player);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(C2S_POWER, (server, player, handler, buf, resp) -> {
            int id = buf.readVarInt();
            float power = buf.readFloat();
            server.execute(() -> {
                Entry e = ENTRIES.get(id);
                if (e == null) return;
                FalseFlowerBlockEntity be = e.get();
                if (be == null) return;

                // ✅ One-shots: cannot adjust range while activating/arming
                FairySpell s = be.getSpell();
                if (s != null && s.isOneShot() && be.isArming(e.world)) {
                    // ignore the request (snapshot will still refresh)
                    sendSnapshot(player);
                    return;
                }

                be.setPower(clamp(power, 1f, 3f));
                sendSnapshot(player);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(C2S_RENAME, (server, player, handler, buf, resp) -> {
            int id = buf.readVarInt();
            String name = buf.readString(64);
            server.execute(() -> {
                Entry e = ENTRIES.get(id);
                if (e == null) return;
                FalseFlowerBlockEntity be = e.get();
                if (be != null) be.setCustomName(name);
                sendSnapshot(player);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(C2S_CLEANSE, (server, player, handler, buf, resp) -> {
            int id = buf.readVarInt();
            server.execute(() -> {
                Entry e = ENTRIES.get(id);
                if (e == null) return;
                FalseFlowerBlockEntity be = e.get();
                if (be != null) be.cleanse();
                sendSnapshot(player);
            });
        });

        // ✅ push snapshots automatically so aura visuals update without opening menus
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ServerWorld ow = server.getOverworld();
            if (ow == null) return;

            long now = ow.getTime();
            if (now == lastBroadcastTick) return;
            if ((now % SNAPSHOT_BROADCAST_INTERVAL_TICKS) != 0) return;

            lastBroadcastTick = now;
            broadcastSnapshot(server);
        });
    }

    @Environment(EnvType.CLIENT)
    public static void registerClient() {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                S2C_SNAPSHOT, (client, handler, buf, resp) -> {
                    List<ClientEntry> list = readSnapshot(buf);
                    client.execute(() -> CLIENT_ENTRIES = list);
                });
    }

    public static void sendSnapshot(ServerPlayerEntity p) {
        ServerPlayNetworking.send(p, S2C_SNAPSHOT, buildSnapshotBuf());
    }

    private static void broadcastSnapshot(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(p, S2C_SNAPSHOT, buildSnapshotBuf());
        }
    }

    private static PacketByteBuf buildSnapshotBuf() {
        PacketByteBuf out = PacketByteBufs.create();
        var es = new ArrayList<>(ENTRIES.values());

        List<Entry> kept = new ArrayList<>();
        for (Entry e : es) {
            FalseFlowerBlockEntity be = e.get();
            if (be == null) continue;
            if (!be.isMagical()) continue;
            kept.add(e);
        }

        out.writeVarInt(kept.size());
        for (Entry e : kept) {
            FalseFlowerBlockEntity be = e.get();
            if (be == null) continue;

            String spellKey = be.getSpellKey();
            FairySpell s = FairySpell.fromTextureKey(spellKey);
            int rgb = s.colorRgb();

            boolean oneShot = s.isOneShot();
            float armProg = 0f;
            int armDur = 0;

            if (oneShot) {
                armProg = be.getArmProgress(e.world);
                armDur = be.getArmDurationTicks();
            }

            out.writeVarInt(e.id);
            out.writeBlockPos(e.pos);
            out.writeString(be.getCustomName() == null ? "" : be.getCustomName());
            out.writeBoolean(be.isActive());
            out.writeFloat(be.getMana());
            out.writeFloat(be.getPower());
            out.writeString(spellKey);
            out.writeInt(rgb);

            // one-shot timing for client sigil visuals
            out.writeBoolean(oneShot);
            out.writeFloat(armProg);
            out.writeVarInt(armDur);
        }
        return out;
    }

    private static List<ClientEntry> readSnapshot(PacketByteBuf buf) {
        int n = buf.readVarInt();
        List<ClientEntry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int id = buf.readVarInt();
            BlockPos pos = buf.readBlockPos();
            String name = buf.readString();
            boolean active = buf.readBoolean();
            float mana = buf.readFloat();
            float power = buf.readFloat();
            String spellKey = buf.readString();
            int rgb = buf.readInt();

            boolean oneShot = buf.readBoolean();
            float armProg = buf.readFloat();
            int armDur = buf.readVarInt();

            list.add(new ClientEntry(id, name, pos, active, mana, power, spellKey, rgb, oneShot, armProg, armDur));
        }
        return list;
    }

    private static float clamp(float v, float a, float b) { return v < a ? a : Math.min(v, b); }

    private static List<ClientEntry> CLIENT_ENTRIES = List.of();
    public static List<ClientEntry> clientSnapshot() { return CLIENT_ENTRIES; }

    @Environment(EnvType.CLIENT)
    public static void requestSnapshot() {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(C2S_REQ_SNAPSHOT, PacketByteBufs.create());
    }

    @Environment(EnvType.CLIENT)
    public static void sendToggle(int id, boolean active) {
        PacketByteBuf b = PacketByteBufs.create();
        b.writeVarInt(id);
        b.writeBoolean(active);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(C2S_TOGGLE, b);
    }

    @Environment(EnvType.CLIENT)
    public static void sendPower(int id, float power) {
        PacketByteBuf b = PacketByteBufs.create();
        b.writeVarInt(id);
        b.writeFloat(power);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(C2S_POWER, b);
    }

    @Environment(EnvType.CLIENT)
    public static void sendRename(int id, String name) {
        PacketByteBuf b = PacketByteBufs.create();
        b.writeVarInt(id);
        b.writeString(name == null ? "" : name);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(C2S_RENAME, b);
    }

    @Environment(EnvType.CLIENT)
    public static void sendCleanse(int id) {
        PacketByteBuf b = PacketByteBufs.create();
        b.writeVarInt(id);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(C2S_CLEANSE, b);
    }

    private static final class Entry {
        final int id;
        final ServerWorld world;
        final BlockPos pos;

        Entry(int id, ServerWorld w, BlockPos pos) {
            this.id = id; this.world = w; this.pos = pos;
        }

        FalseFlowerBlockEntity get() {
            BlockEntity be = world.getBlockEntity(pos);
            return (be instanceof FalseFlowerBlockEntity f) ? f : null;
        }
    }

    public record ClientEntry(
            int id,
            String name,
            BlockPos pos,
            boolean active,
            float mana,
            float power,
            String spellKey,
            int spellColorRgb,

            boolean oneShot,
            float armProgress,
            int armDurationTicks
    ) {}
}
