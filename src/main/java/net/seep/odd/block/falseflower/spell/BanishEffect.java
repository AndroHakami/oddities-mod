// src/main/java/net/seep/odd/block/falseflower/spell/BanishEffect.java
package net.seep.odd.block.falseflower.spell;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.PersistentState;
import net.seep.odd.block.falseflower.FalseFlowerBlockEntity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class BanishEffect implements FalseFlowerSpellEffect {

    private static final Identifier VOID_DIM_ID = new Identifier("odd", "the_void");
    private static final long DURATION_TICKS = 20L * 20L; // 20 seconds

    @Override
    public void tick(ServerWorld w, BlockPos pos, BlockState state, FalseFlowerBlockEntity be, int R, Box box) {
        // One-shot: run once then turn off
        Vec3d c = Vec3d.ofCenter(pos);

        // ✅ SNAPSHOT LIST FIRST (prevents CME when teleporting across worlds)
        var found = w.getEntitiesByClass(ServerPlayerEntity.class, box,
                p -> p.isAlive() && FalseFlowerSpellUtil.insideSphere(p.getPos(), c, R));
        var targets = new ArrayList<>(found);

        MinecraftServer server = w.getServer();
        RegistryKey<net.minecraft.world.World> voidKey = RegistryKey.of(RegistryKeys.WORLD, VOID_DIM_ID);
        ServerWorld voidWorld = server.getWorld(voidKey);

        if (targets.isEmpty() || voidWorld == null) {
            be.setMana(0);
            be.setActive(false);
            return;
        }

        long returnAt = w.getTime() + DURATION_TICKS;
        BanishState st = BanishState.of(server);

        for (ServerPlayerEntity p : targets) {
            // store where they came from BEFORE moving them
            st.put(p, w, p.getPos(), p.getYaw(), p.getPitch(), returnAt);

            // teleport into your void dimension
// ✅ fixed spawn in odd:the_void
            p.teleport(voidWorld, 0.5, 73.0, 0.5, p.getYaw(), p.getPitch());

        }

        st.markDirty();

        be.setMana(0);
        be.setActive(false);
    }

    /**
     * Call once per server tick (NOT per flower) to return players.
     * This uses Iterator removal to avoid CME.
     */
    public static void tickReturns(MinecraftServer server) {
        BanishState st = BanishState.of(server);
        if (st.map.isEmpty()) return;

        Iterator<Map.Entry<UUID, Ticket>> it = st.map.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            UUID id = e.getKey();
            Ticket t = e.getValue();

            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            if (p == null) continue;

            ServerWorld timeWorld = server.getWorld(t.returnWorld);
            long now = (timeWorld != null) ? timeWorld.getTime() : server.getOverworld().getTime();
            if (now < t.returnAt) continue;

            ServerWorld dest = server.getWorld(t.returnWorld);
            if (dest != null) {
                p.teleport(dest, t.x, t.y, t.z, t.yaw, t.pitch);
            }

            it.remove();
            st.markDirty();
        }
    }

    /* ================= Persistent State ================= */

    private static final class BanishState extends PersistentState {
        final Object2ObjectOpenHashMap<UUID, Ticket> map = new Object2ObjectOpenHashMap<>();

        void put(ServerPlayerEntity p, ServerWorld fromWorld, Vec3d pos, float yaw, float pitch, long returnAt) {
            map.put(p.getUuid(), new Ticket(fromWorld.getRegistryKey(), pos.x, pos.y, pos.z, yaw, pitch, returnAt));
        }

        @Override
        public NbtCompound writeNbt(NbtCompound nbt) {
            NbtList list = new NbtList();
            for (var e : map.entrySet()) {
                UUID id = e.getKey();
                Ticket t = e.getValue();

                NbtCompound c = new NbtCompound();
                c.putUuid("id", id);
                c.putString("w", t.returnWorld.getValue().toString());
                c.putDouble("x", t.x);
                c.putDouble("y", t.y);
                c.putDouble("z", t.z);
                c.putFloat("yaw", t.yaw);
                c.putFloat("pitch", t.pitch);
                c.putLong("at", t.returnAt);
                list.add(c);
            }
            nbt.put("list", list);
            return nbt;
        }

        static BanishState fromNbt(NbtCompound nbt) {
            BanishState s = new BanishState();
            NbtList list = nbt.getList("list", NbtElement.COMPOUND_TYPE);

            for (int i = 0; i < list.size(); i++) {
                NbtCompound c = list.getCompound(i);
                UUID id = c.getUuid("id");

                Identifier wid = Identifier.tryParse(c.getString("w"));
                if (wid == null) continue;

                RegistryKey<net.minecraft.world.World> wkey = RegistryKey.of(RegistryKeys.WORLD, wid);
                Ticket t = new Ticket(
                        wkey,
                        c.getDouble("x"), c.getDouble("y"), c.getDouble("z"),
                        c.getFloat("yaw"), c.getFloat("pitch"),
                        c.getLong("at")
                );
                s.map.put(id, t);
            }
            return s;
        }

        static BanishState of(MinecraftServer server) {
            return server.getOverworld().getPersistentStateManager()
                    .getOrCreate(BanishState::fromNbt, BanishState::new, "odd_false_flower_banish");
        }
    }

    private record Ticket(
            RegistryKey<net.minecraft.world.World> returnWorld,
            double x, double y, double z,
            float yaw, float pitch,
            long returnAt
    ) {}
}
