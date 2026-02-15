// FILE: src/main/java/net/seep/odd/abilities/wizard/WizardTempEntities.java
package net.seep.odd.abilities.wizard;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.PersistentState;

import net.seep.odd.mixin.BlockDisplayEntityInvoker;

import java.util.Iterator;
import java.util.UUID;

public final class WizardTempEntities {
    private WizardTempEntities() {}

    public static void spawnIceCage(ServerWorld sw, Entity target, int durationTicks) {
        Vec3d c = target.getPos().add(0, 0.1, 0);
        long expire = sw.getTime() + durationTicks;

        double[][] offsets = {
                { 0.7, 0.0,  0.7}, {-0.7, 0.0,  0.7}, { 0.7, 0.0, -0.7}, {-0.7, 0.0, -0.7},
                { 0.7, 1.1,  0.7}, {-0.7, 1.1,  0.7}, { 0.7, 1.1, -0.7}, {-0.7, 1.1, -0.7},
        };

        for (double[] o : offsets) {
            DisplayEntity.BlockDisplayEntity bd =
                    new DisplayEntity.BlockDisplayEntity(EntityType.BLOCK_DISPLAY, sw);

            ((BlockDisplayEntityInvoker)(Object) bd).odd$setBlockState(Blocks.ICE.getDefaultState());

            bd.refreshPositionAndAngles(c.x + o[0], c.y + o[1], c.z + o[2], 0f, 0f);
            bd.setNoGravity(true);
            sw.spawnEntity(bd);

            TempState.of(sw).trackStatic(bd.getUuid(), expire);
        }
    }

    /**
     * NEW: “big ice cube” around the target, proportional to its size.
     * Uses BlockDisplay entities + rise animation so it feels dynamic.
     */
    public static void spawnIceCube(ServerWorld sw, LivingEntity target, int durationTicks) {
        Vec3d c = target.getPos();
        long now = sw.getTime();
        long expire = now + durationTicks;

        float w = Math.max(0.8f, target.getWidth());
        float h = Math.max(1.0f, target.getHeight());

        double xOff = (w * 0.65) + 0.65;
        double zOff = (w * 0.65) + 0.65;
        double yTop = h + 0.25;

        double yMid = yTop * 0.5;

        // points that read like a cube: 8 corners + 4 face-centers at mid height
        double[][] points = {
                { +xOff, 0.05, +zOff }, { -xOff, 0.05, +zOff }, { +xOff, 0.05, -zOff }, { -xOff, 0.05, -zOff },
                { +xOff, yTop, +zOff }, { -xOff, yTop, +zOff }, { +xOff, yTop, -zOff }, { -xOff, yTop, -zOff },

                { +xOff, yMid, 0.0 }, { -xOff, yMid, 0.0 }, { 0.0, yMid, +zOff }, { 0.0, yMid, -zOff }
        };

        final int riseTicks = 10;

        for (double[] p : points) {
            DisplayEntity.BlockDisplayEntity bd =
                    new DisplayEntity.BlockDisplayEntity(EntityType.BLOCK_DISPLAY, sw);

            ((BlockDisplayEntityInvoker)(Object) bd).odd$setBlockState(Blocks.ICE.getDefaultState());
            bd.setNoGravity(true);

            double endX = c.x + p[0];
            double endY = c.y + p[1];
            double endZ = c.z + p[2];

            double startY = endY - 1.10;

            bd.refreshPositionAndAngles(endX, startY, endZ, 0f, 0f);
            sw.spawnEntity(bd);

            TempState.of(sw).trackRise(bd.getUuid(), expire, startY, endY, now, riseTicks);
        }

        // “animated” feel: snow burst
        sw.spawnParticles(ParticleTypes.SNOWFLAKE,
                target.getX(), target.getBodyY(0.5), target.getZ(),
                90,
                w * 0.9, h * 0.6, w * 0.9,
                0.02);
        sw.spawnParticles(ParticleTypes.CLOUD,
                target.getX(), target.getBodyY(0.3), target.getZ(),
                40,
                w * 0.7, h * 0.4, w * 0.7,
                0.01);
    }

    /**
     * Animated eruption ring: ice starts below ground and rises up quickly.
     * Duration is total lifetime; rise animation happens only at the start.
     */
    public static void spawnIceEruption(ServerWorld sw, Vec3d at, int durationTicks) {
        long now = sw.getTime();
        long expire = now + durationTicks;

        double[][] offsets = {
                { 1.05, 0.0,  0.0}, {-1.05, 0.0,  0.0}, { 0.0, 0.0,  1.05}, { 0.0, 0.0, -1.05},
                { 0.75, 0.0,  0.75}, {-0.75, 0.0,  0.75}, { 0.75, 0.0, -0.75}, {-0.75, 0.0, -0.75},
        };

        final int riseTicks = 10;
        final double startY = at.y - 0.85;
        final double endY   = at.y + 0.10;

        for (double[] o : offsets) {
            DisplayEntity.BlockDisplayEntity bd =
                    new DisplayEntity.BlockDisplayEntity(EntityType.BLOCK_DISPLAY, sw);

            ((BlockDisplayEntityInvoker)(Object) bd).odd$setBlockState(Blocks.ICE.getDefaultState());

            bd.setNoGravity(true);
            bd.refreshPositionAndAngles(at.x + o[0], startY, at.z + o[2], 0f, 0f);
            sw.spawnEntity(bd);

            TempState.of(sw).trackRise(bd.getUuid(), expire, startY, endY, now, riseTicks);
        }
    }

    public static void tickCleanup(ServerWorld sw) {
        TempState st = TempState.of(sw);
        if (st.entries.isEmpty()) return;

        long now = sw.getTime();
        boolean dirty = false;

        Iterator<Entry> it = st.entries.iterator();
        while (it.hasNext()) {
            Entry e = it.next();

            // ---- animate (if needed) ----
            if (e.riseTicks > 0) {
                long age = now - e.spawnTick;
                if (age >= 0 && age < e.riseTicks) {
                    Entity ent = sw.getEntity(e.id);
                    if (ent != null) {
                        float a = age / (float) e.riseTicks;
                        double y = MathHelper.lerp(a, e.startY, e.endY);
                        ent.requestTeleport(ent.getX(), y, ent.getZ());
                    }
                }
            }

            // ---- cleanup when expired ----
            if (now < e.expireTick) continue;

            Entity ent = sw.getEntity(e.id);
            if (ent != null) ent.discard();

            it.remove();
            dirty = true;
        }

        if (dirty) st.markDirty();
    }

    private record Entry(UUID id, long expireTick,
                         double startY, double endY,
                         long spawnTick, int riseTicks) {}

    private static final class TempState extends PersistentState {
        private final ObjectArrayList<Entry> entries = new ObjectArrayList<>();

        void trackStatic(UUID id, long expireTick) {
            entries.add(new Entry(id, expireTick, 0.0, 0.0, 0L, 0));
            markDirty();
        }

        void trackRise(UUID id, long expireTick, double startY, double endY, long spawnTick, int riseTicks) {
            entries.add(new Entry(id, expireTick, startY, endY, spawnTick, Math.max(0, riseTicks)));
            markDirty();
        }

        @Override
        public NbtCompound writeNbt(NbtCompound nbt) {
            NbtList list = new NbtList();
            for (Entry e : entries) {
                NbtCompound c = new NbtCompound();
                c.putUuid("id", e.id);
                c.putLong("t", e.expireTick);

                if (e.riseTicks > 0) {
                    c.putDouble("sy", e.startY);
                    c.putDouble("ey", e.endY);
                    c.putLong("st", e.spawnTick);
                    c.putInt("rt", e.riseTicks);
                }

                list.add(c);
            }
            nbt.put("list", list);
            return nbt;
        }

        static TempState fromNbt(NbtCompound nbt) {
            TempState s = new TempState();
            NbtList list = nbt.getList("list", NbtElement.COMPOUND_TYPE);

            for (int i = 0; i < list.size(); i++) {
                NbtCompound c = list.getCompound(i);

                UUID id = c.getUuid("id");
                long t = c.getLong("t");

                double sy = c.contains("sy") ? c.getDouble("sy") : 0.0;
                double ey = c.contains("ey") ? c.getDouble("ey") : 0.0;
                long stTick = c.contains("st") ? c.getLong("st") : 0L;
                int rt = c.contains("rt") ? c.getInt("rt") : 0;

                s.entries.add(new Entry(id, t, sy, ey, stTick, rt));
            }
            return s;
        }

        static TempState of(ServerWorld world) {
            return world.getPersistentStateManager().getOrCreate(
                    TempState::fromNbt,
                    TempState::new,
                    "odd_wizard_temp"
            );
        }
    }
}
