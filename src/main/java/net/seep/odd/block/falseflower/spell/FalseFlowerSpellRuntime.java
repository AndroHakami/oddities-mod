// src/main/java/net/seep/odd/block/falseflower/spell/FalseFlowerSpellRuntime.java
package net.seep.odd.block.falseflower.spell;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class FalseFlowerSpellRuntime {
    private FalseFlowerSpellRuntime() {}

    /** Your void dimension: odd:the_void */
    public static final RegistryKey<World> VOID_DIM =
            RegistryKey.of(RegistryKeys.WORLD, new Identifier("odd", "the_void"));

    private static final int ACTIONBAR_EVERY = 20; // 1s

    /**
     * ✅ IMPORTANT:
     * We must tick these maps ONCE per SERVER TICK (not once per world),
     * otherwise time comparisons break (world time differs) and you can get
     * multi-processing / CME style issues when teleports happen.
     */
    private static long LAST_SERVER_TICK = Long.MIN_VALUE;

    private static final Object2ObjectOpenHashMap<UUID, TimedReturn> BANISH = new Object2ObjectOpenHashMap<>();
    private static final Object2ObjectOpenHashMap<UUID, TimedReturn> RETURN = new Object2ObjectOpenHashMap<>();
    private static final Object2LongOpenHashMap<UUID> TINY_LAST_SEEN = new Object2LongOpenHashMap<>();

    /**
     * Call from FalseFlowerBlockEntity.tick(sw) — it's fine if many flowers call it;
     * this method guards so it runs only once per server tick.
     */
    public static void tick(ServerWorld anyWorld) {
        MinecraftServer server = anyWorld.getServer();
        long now = server.getOverworld().getTime(); // ✅ canonical tick time

        if (LAST_SERVER_TICK == now) return;
        LAST_SERVER_TICK = now;

        tickBanish(server, now);
        tickReturn(server, now);
        tickTinyCleanup(server, now);
    }

    /* ================= API used by effects ================= */

    public static void banish(ServerPlayerEntity sp, long durationTicks) {
        MinecraftServer server = sp.getServer();
        if (server == null) return;

        ServerWorld from = (ServerWorld) sp.getWorld();
        long now = server.getOverworld().getTime();
        long expire = now + durationTicks;

        BANISH.put(sp.getUuid(), new TimedReturn(
                from.getRegistryKey(),
                sp.getPos(),
                sp.getYaw(),
                sp.getPitch(),
                expire
        ));

        ServerWorld voidWorld = server.getWorld(VOID_DIM);
        if (voidWorld != null) {
            // ✅ requested location
            sp.teleport(voidWorld, 0.5, 73.0, 0.5, sp.getYaw(), sp.getPitch());
        } else {
            sp.sendMessage(Text.literal("Missing void dimension: " + VOID_DIM.getValue()), true);
        }
    }

    public static void setReturnPoint(ServerPlayerEntity sp, long durationTicks) {
        MinecraftServer server = sp.getServer();
        if (server == null) return;

        ServerWorld w = (ServerWorld) sp.getWorld();
        long now = server.getOverworld().getTime();
        long expire = now + durationTicks;

        RETURN.put(sp.getUuid(), new TimedReturn(
                w.getRegistryKey(),
                sp.getPos(),
                sp.getYaw(),
                sp.getPitch(),
                expire
        ));
    }

    public static void markTiny(ServerPlayerEntity sp) {
        MinecraftServer server = sp.getServer();
        if (server == null) return;
        long now = server.getOverworld().getTime();
        TINY_LAST_SEEN.put(sp.getUuid(), now);
    }

    /* ================= Internal ticking ================= */

    private static void tickBanish(MinecraftServer server, long now) {
        if (BANISH.isEmpty()) return;

        Iterator<Map.Entry<UUID, TimedReturn>> it = BANISH.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            UUID id = e.getKey();
            TimedReturn tr = e.getValue();

            ServerPlayerEntity sp = server.getPlayerManager().getPlayer(id);
            if (sp == null) {
                if (now >= tr.expireTick) it.remove();
                continue;
            }

            long left = tr.expireTick - now;
            if (left <= 0) {
                ServerWorld back = server.getWorld(tr.worldKey);
                if (back != null) sp.teleport(back, tr.pos.x, tr.pos.y, tr.pos.z, tr.yaw, tr.pitch);
                sp.sendMessage(Text.literal("Returned."), true);
                it.remove();
            } else if (left % ACTIONBAR_EVERY == 0) {
                sp.sendMessage(Text.literal("Banish: " + (left / 20) + "s"), true);
            }
        }
    }

    private static void tickReturn(MinecraftServer server, long now) {
        if (RETURN.isEmpty()) return;

        Iterator<Map.Entry<UUID, TimedReturn>> it = RETURN.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            UUID id = e.getKey();
            TimedReturn tr = e.getValue();

            ServerPlayerEntity sp = server.getPlayerManager().getPlayer(id);
            if (sp == null) {
                if (now >= tr.expireTick) it.remove();
                continue;
            }

            long left = tr.expireTick - now;
            if (left <= 0) {
                ServerWorld back = server.getWorld(tr.worldKey);
                if (back != null) sp.teleport(back, tr.pos.x, tr.pos.y, tr.pos.z, tr.yaw, tr.pitch);
                sp.sendMessage(Text.literal("Snapped back."), true);
                it.remove();
            } else if (left % ACTIONBAR_EVERY == 0) {
                sp.sendMessage(Text.literal("Return in: " + (left / 20) + "s"), true);
            }
        }
    }

    private static void tickTinyCleanup(MinecraftServer server, long now) {
        if (TINY_LAST_SEEN.isEmpty()) return;

        var it = TINY_LAST_SEEN.object2LongEntrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            UUID id = e.getKey();
            long last = e.getLongValue();

            if (now - last > 12) { // left the aura recently
                it.remove();
                ServerPlayerEntity sp = server.getPlayerManager().getPlayer(id);
                if (sp != null) FalseFlowerSpellUtil.setPehkuiBaseScale(sp, 1.0f);
            }
        }
    }

    public record TimedReturn(RegistryKey<World> worldKey, Vec3d pos, float yaw, float pitch, long expireTick) {}
}
