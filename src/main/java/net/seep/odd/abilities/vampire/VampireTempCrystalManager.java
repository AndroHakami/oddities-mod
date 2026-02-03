// src/main/java/net/seep/odd/abilities/vampire/VampireTempCrystalManager.java
package net.seep.odd.abilities.vampire;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtLongArray;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.seep.odd.block.ModBlocks;

import java.util.Iterator;

/**
 * Temporary blood crystal spike structures that delete themselves after a lifetime.
 * Self-ticks via ServerTickEvents.END_WORLD_TICK (NOT tied to VampirePower tick).
 */
public final class VampireTempCrystalManager {
    private VampireTempCrystalManager() {}

    // 20 ticks = 1 second
    public static final int DEFAULT_LIFETIME_TICKS = 20 * 8; // 25s

    private static final String STATE_KEY = "odd_vampire_temp_crystals";
    private static boolean inited = false;

    /** Call once during mod init (safe to call multiple times). */
    public static void init() {
        if (inited) return;
        inited = true;

        ServerTickEvents.END_WORLD_TICK.register(VampireTempCrystalManager::tickWorld);
    }

    private static void tickWorld(ServerWorld world) {
        tick(world);
    }

    /** Main tick: deletes expired structures even if no vampires are online. */
    public static void tick(ServerWorld world) {
        State st = State.of(world);
        long now = world.getTime();

        if (st.spikes.isEmpty()) return;

        int deletionsBudget = 8;

        for (Iterator<Spike> it = st.spikes.iterator(); it.hasNext() && deletionsBudget > 0; ) {
            Spike s = it.next();
            if (now < s.expiresAt) continue;

            forceLoadChunksFor(world, s.blocks);

            int fxBudget = 140;

            if (s.blocks.length > 0) {
                BlockPos soundPos = BlockPos.fromLong(s.blocks[0]);
                BlockState soundState = world.getBlockState(soundPos);
                if (soundState.isOf(ModBlocks.BLOOD_CRYSTAL) || soundState.isOf(ModBlocks.BLOOD_CRYSTAL_BLOCK)) {
                    world.playSound(
                            null,
                            soundPos,
                            soundState.getSoundGroup().getBreakSound(),
                            net.minecraft.sound.SoundCategory.BLOCKS,
                            0.9f,
                            0.85f + world.getRandom().nextFloat() * 0.25f
                    );
                }
            }

            for (long lp : s.blocks) {
                BlockPos p = BlockPos.fromLong(lp);
                BlockState bs = world.getBlockState(p);

                if (bs.isOf(ModBlocks.BLOOD_CRYSTAL) || bs.isOf(ModBlocks.BLOOD_CRYSTAL_BLOCK)) {
                    if (fxBudget-- > 0) {
                        world.syncWorldEvent(2001, p, Block.getRawIdFromState(bs)); // block break FX
                    }
                    world.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                }
            }

            it.remove();
            st.markDirty();
            deletionsBudget--;
        }
    }

    /**
     * Spawn a spike structure.
     *
     * @param world server world
     * @param hitBlockPos the block we hit
     * @param tipDirection direction the spike points toward (usually BlockHitResult.getSide()).
     */
    public static void spawnSpike(ServerWorld world, BlockPos hitBlockPos, Direction tipDirection) {
        State st = State.of(world);

        // Start on the air side of the hit face
        BlockPos start = hitBlockPos.offset(tipDirection);

        Random r = world.getRandom();

        // Smaller + slimmer
        int len = 4 + r.nextInt(4);        // 4..7
        int baseRadius = 1 + r.nextInt(2); // 1..2

        LongArrayList placed = new LongArrayList();
        AxisPair axes = axisPairFor(tipDirection);

        // ✅ CUMULATIVE wobble offsets (prevents harsh diagonal disconnects)
        int offA = 0;
        int offB = 0;

        BlockPos prevCenter = start;
        BlockPos lastCenter = start;

        for (int i = 0; i < len; i++) {
            // taper (slimmer near tip)
            float t = 1.0f - (i / (float)(len - 1));
            t = t * t; // sharper taper
            int rad = Math.max(0, Math.round(baseRadius * t));

            // ✅ Only change ONE axis per step (never both),
            // so consecutive slices stay face-connected.
            if (i > 0 && r.nextFloat() < 0.35f) {
                boolean changeA = r.nextBoolean();
                int delta = r.nextBoolean() ? 1 : -1;
                if (changeA) offA = MathHelper.clamp(offA + delta, -1, 1);
                else         offB = MathHelper.clamp(offB + delta, -1, 1);
            }

            BlockPos center = start.offset(tipDirection, i)
                    .offset(axes.a, offA)
                    .offset(axes.b, offB);

            // ✅ Bridge between prev and current center so NO gaps ever occur
            bridgeCenters(world, prevCenter, center, placed);

            prevCenter = center;
            lastCenter = center;

            // Cross-section (ALL BLOOD_CRYSTAL_BLOCK)
            for (int da = -rad; da <= rad; da++) {
                for (int db = -rad; db <= rad; db++) {
                    int manhattan = Math.abs(da) + Math.abs(db);
                    if (manhattan > rad) continue;

                    BlockPos p = center.offset(axes.a, da).offset(axes.b, db);
                    placeIfPossible(world, p, ModBlocks.BLOOD_CRYSTAL_BLOCK.getDefaultState(), placed);
                }
            }
        }

        // ✅ Put 1–2 BLOOD_CRYSTAL only at the tip
        placeTipCrystal(world, lastCenter, tipDirection, placed);
        if (r.nextBoolean()) {
            placeTipCrystal(world, lastCenter.offset(tipDirection), tipDirection, placed);
        }

        if (!placed.isEmpty()) {
            st.spikes.add(new Spike(world.getTime() + DEFAULT_LIFETIME_TICKS, placed.toLongArray()));
            st.markDirty();
        }
    }

    /* ---------------- helpers ---------------- */

    private static void placeIfPossible(ServerWorld world, BlockPos p, BlockState state, LongArrayList placed) {
        BlockState cur = world.getBlockState(p);
        if (!cur.isAir() && !cur.isReplaceable()) return;
        world.setBlockState(p, state, Block.NOTIFY_ALL);
        placed.add(p.asLong());
    }

    // Bridges along perpendicular axes so connection is always face-adjacent
    private static void bridgeCenters(ServerWorld world, BlockPos from, BlockPos to, LongArrayList placed) {
        if (from.equals(to)) return;

        BlockPos delta = to.subtract(from);

        // We only expect movement on 2 perpendicular axes, but do it generically:
        int dx = delta.getX();
        int dy = delta.getY();
        int dz = delta.getZ();

        BlockPos cur = from;

        // Step X
        int sx = Integer.compare(dx, 0);
        for (int i = 0; i < Math.abs(dx); i++) {
            cur = cur.add(sx, 0, 0);
            placeIfPossible(world, cur, ModBlocks.BLOOD_CRYSTAL_BLOCK.getDefaultState(), placed);
        }

        // Step Y
        int sy = Integer.compare(dy, 0);
        for (int i = 0; i < Math.abs(dy); i++) {
            cur = cur.add(0, sy, 0);
            placeIfPossible(world, cur, ModBlocks.BLOOD_CRYSTAL_BLOCK.getDefaultState(), placed);
        }

        // Step Z
        int sz = Integer.compare(dz, 0);
        for (int i = 0; i < Math.abs(dz); i++) {
            cur = cur.add(0, 0, sz);
            placeIfPossible(world, cur, ModBlocks.BLOOD_CRYSTAL_BLOCK.getDefaultState(), placed);
        }
    }

    private static void placeTipCrystal(ServerWorld world, BlockPos pos, Direction tipDirection, LongArrayList placed) {
        BlockState cur = world.getBlockState(pos);
        if (!cur.isAir() && !cur.isReplaceable()) return;

        BlockState tip = orientedCrystalState(tipDirection);
        world.setBlockState(pos, tip, Block.NOTIFY_ALL);
        placed.add(pos.asLong());
    }

    private static BlockState orientedCrystalState(Direction tipDirection) {
        BlockState s = ModBlocks.BLOOD_CRYSTAL.getDefaultState();
        if (s.contains(Properties.FACING)) return s.with(Properties.FACING, tipDirection);
        return s;
    }

    private static void forceLoadChunksFor(ServerWorld world, long[] blocks) {
        LongOpenHashSet chunks = new LongOpenHashSet();
        for (long lp : blocks) {
            BlockPos p = BlockPos.fromLong(lp);
            chunks.add(ChunkPos.toLong(p.getX() >> 4, p.getZ() >> 4));
        }

        for (long ck : chunks) {
            int cx = ChunkPos.getPackedX(ck);
            int cz = ChunkPos.getPackedZ(ck);
            world.getChunk(cx, cz);
        }
    }

    private static AxisPair axisPairFor(Direction d) {
        return switch (d.getAxis()) {
            case Y -> new AxisPair(Direction.EAST, Direction.SOUTH);  // X/Z plane
            case X -> new AxisPair(Direction.UP, Direction.SOUTH);    // Y/Z plane
            case Z -> new AxisPair(Direction.UP, Direction.EAST);     // Y/X plane
        };
    }

    private record AxisPair(Direction a, Direction b) {}

    /* ---------------- persistent state ---------------- */

    private static final class Spike {
        final long expiresAt;
        final long[] blocks;

        Spike(long expiresAt, long[] blocks) {
            this.expiresAt = expiresAt;
            this.blocks = blocks;
        }
    }

    private static final class State extends net.minecraft.world.PersistentState {
        final ObjectArrayList<Spike> spikes = new ObjectArrayList<>();

        @Override
        public NbtCompound writeNbt(NbtCompound nbt) {
            NbtList list = new NbtList();
            for (Spike s : spikes) {
                NbtCompound c = new NbtCompound();
                c.putLong("t", s.expiresAt);
                c.put("b", new NbtLongArray(s.blocks));
                list.add(c);
            }
            nbt.put("spikes", list);
            return nbt;
        }

        static State fromNbt(NbtCompound nbt) {
            State st = new State();
            NbtList list = nbt.getList("spikes", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < list.size(); i++) {
                NbtCompound c = list.getCompound(i);
                long t = c.getLong("t");
                long[] b = c.getLongArray("b");
                if (b.length > 0) st.spikes.add(new Spike(t, b));
            }
            return st;
        }

        static State of(ServerWorld world) {
            return world.getPersistentStateManager().getOrCreate(State::fromNbt, State::new, STATE_KEY);
        }
    }
}
