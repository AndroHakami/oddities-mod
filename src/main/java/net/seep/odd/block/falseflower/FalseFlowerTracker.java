package net.seep.odd.block.falseflower;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.seep.odd.Oddities;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Simple registry + net sync for False Flowers. */
public final class FalseFlowerTracker {
    private FalseFlowerTracker() {}

    /* ---------------- Net ---------------- */
    public static final Identifier C2S_REQ_SNAPSHOT = id("fairy/flowers/request");
    public static final Identifier S2C_SNAPSHOT     = id("fairy/flowers/snapshot");
    public static final Identifier C2S_TOGGLE       = id("fairy/flowers/toggle");
    public static final Identifier C2S_POWER        = id("fairy/flowers/power");
    public static final Identifier C2S_RENAME       = id("fairy/flowers/rename");

    private static Identifier id(String path) { return new Identifier(Oddities.MOD_ID, path); }

    /* ---------------- Server registry ---------------- */
    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);
    private static final Int2ObjectOpenHashMap<Entry> ENTRIES = new Int2ObjectOpenHashMap<>();

    /** Attach/detach from BE lifecycle. Call in BE#onLoad and #markRemoved. */
    public static void hook(FalseFlowerBlockEntity be) {
        if (!(be.getWorld() instanceof ServerWorld sw)) return;
        if (be.trackerId == 0) be.trackerId = NEXT_ID.getAndIncrement();
        ENTRIES.put(be.trackerId, new Entry(be.trackerId, sw, be.getPos()));
    }
    public static void unhook(FalseFlowerBlockEntity be) {
        if (be.trackerId != 0) ENTRIES.remove(be.trackerId);
    }

    /* ---------------- Registration ---------------- */

    /** Call from common/server init. Safe on dedicated servers. */
    public static void registerServer() {
        // request snapshot
        ServerPlayNetworking.registerGlobalReceiver(C2S_REQ_SNAPSHOT, (server, player, handler, buf, resp) ->
                server.execute(() -> sendSnapshot(player)));

        // toggle active
        ServerPlayNetworking.registerGlobalReceiver(C2S_TOGGLE, (server, player, handler, buf, resp) -> {
            int id = buf.readVarInt();
            boolean active = buf.readBoolean();
            server.execute(() -> {
                Entry e = ENTRIES.get(id);
                if (e == null) return;
                FalseFlowerBlockEntity be = e.get();
                if (be != null) {
                    be.setActive(active);
                    player.sendMessage(Text.literal((active ? "Activated " : "Deactivated ") + displayName(be)), true);
                }
            });
        });

        // set power (clamped to POWER property range: 1..3)
        ServerPlayNetworking.registerGlobalReceiver(C2S_POWER, (server, player, handler, buf, resp) -> {
            int id = buf.readVarInt();
            float power = buf.readFloat();
            server.execute(() -> {
                Entry e = ENTRIES.get(id);
                if (e == null) return;
                FalseFlowerBlockEntity be = e.get();
                if (be != null) be.setPower(clamp(power, 1f, 3f));
            });
        });

        // rename
        ServerPlayNetworking.registerGlobalReceiver(C2S_RENAME, (server, player, handler, buf, resp) -> {
            int id = buf.readVarInt();
            String name = buf.readString(64);
            server.execute(() -> {
                Entry e = ENTRIES.get(id);
                if (e == null) return;
                FalseFlowerBlockEntity be = e.get();
                if (be != null) be.setCustomName(name);
            });
        });
    }

    /** Call from your existing OdditiesClient init. */
    @Environment(EnvType.CLIENT)
    public static void registerClient() {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                S2C_SNAPSHOT, (client, handler, buf, resp) -> {
                    List<ClientEntry> list = readSnapshot(buf);
                    client.execute(() -> CLIENT_ENTRIES = list);
                });
    }

    /* ---------------- Snapshot build/send ---------------- */

    /** Public: used by FlowerMenu.openFor(player). */
    public static void sendSnapshot(ServerPlayerEntity p) {
        ServerPlayNetworking.send(p, S2C_SNAPSHOT, buildSnapshotBuf());
    }

    private static PacketByteBuf buildSnapshotBuf() {
        PacketByteBuf out = PacketByteBufs.create();
        var es = new ArrayList<>(ENTRIES.values());
        out.writeVarInt(es.size());
        for (Entry e : es) {
            FalseFlowerBlockEntity be = e.get();
            if (be == null) continue;
            out.writeVarInt(e.id);
            out.writeBlockPos(e.pos);
            out.writeString(be.getCustomName() == null ? "" : be.getCustomName());
            out.writeBoolean(be.isActive());
            out.writeFloat(be.getMana());
            out.writeFloat(be.getPower());
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
            list.add(new ClientEntry(id, name, pos, active, mana, power));
        }
        return list;
    }

    private static float clamp(float v, float a, float b) { return v < a ? a : Math.min(v, b); }

    private static String displayName(FalseFlowerBlockEntity be) {
        String n = be.getCustomName();
        return (n == null || n.isEmpty())
                ? ("Flower@" + be.getPos().getX() + "," + be.getPos().getY() + "," + be.getPos().getZ())
                : n;
    }

    /* ---------------- Client API for UI ---------------- */

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
        b.writeString(name);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(C2S_RENAME, b);
    }

    /* ---------------- Types ---------------- */

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

    /** Client-only DTO used by the UI. */
    public record ClientEntry(int id, String name, BlockPos pos, boolean active, float mana, float power) {}
}
