package net.seep.odd.abilities.artificer.mixer.brew;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;

public final class DismantleEffect {
    private DismantleEffect() {}

    /** 5x5x5 cube */
    public static final int SIZE = 10;
    public static final int HALF_BLOCKS = SIZE / 2; // 2
    public static final float HALF_VISUAL = 4.20f;   // shader half-size in world units

    /** duration of the field */
    public static final int DURATION_TICKS = 20 * 20; // 8s (change if you want)

    private static boolean inited = false;

    /** server zones (authoritative) */
    private static final Long2ObjectOpenHashMap<Zone> ZONES = new Long2ObjectOpenHashMap<>();

    public record Zone(ServerWorld world, BlockPos center, long endServerTick) {}

    private static void initCommon() {
        if (inited) return;
        inited = true;

        ServerTickEvents.START_SERVER_TICK.register(DismantleEffect::serverTick);
    }

    public static void apply(World world, BlockPos impactPos, @Nullable net.minecraft.entity.LivingEntity thrower, ItemStack stack) {
        if (!(world instanceof ServerWorld sw)) return;
        initCommon();

        // cube centered on impact block
        BlockPos center = impactPos.toImmutable();

        long now = sw.getServer().getTicks();
        long end = now + DURATION_TICKS;

        long id = sw.getRandom().nextLong();
        ZONES.put(id, new Zone(sw, center, end));

        // send visuals + client-side zone copy
        DismantleNet.sendSpawn(sw, id, Vec3d.ofCenter(center), HALF_VISUAL, DURATION_TICKS);

        sw.playSound(null, impactPos, SoundEvents.BLOCK_ANVIL_PLACE, SoundCategory.BLOCKS, 0.55f, 1.8f);
        sw.playSound(null, impactPos, SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.BLOCKS, 0.55f, 1.3f);
    }

    private static void serverTick(MinecraftServer server) {
        long now = server.getTicks();

        // purge expired
        var it = ZONES.long2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            var e = it.next();
            Zone z = e.getValue();
            if (now >= z.endServerTick) it.remove();
        }
    }

    /** True only if the BLOCK and PLAYER are both inside the same active cube. */
    public static boolean isPlayerAndBlockInside(World world, Vec3d playerPos, BlockPos blockPos) {
        if (!(world instanceof ServerWorld sw)) return false;

        long now = sw.getServer().getTicks();

        var it = ZONES.long2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            Zone z = it.next().getValue();
            if (z.world != sw) continue;
            if (now >= z.endServerTick) continue;

            if (insideCubeBlocks(z.center, blockPos) && insideCubePlayer(z.center, playerPos)) return true;
        }
        return false;
    }

    private static boolean insideCubeBlocks(BlockPos c, BlockPos p) {
        return Math.abs(p.getX() - c.getX()) <= HALF_BLOCKS
                && Math.abs(p.getY() - c.getY()) <= HALF_BLOCKS
                && Math.abs(p.getZ() - c.getZ()) <= HALF_BLOCKS;
    }

    private static boolean insideCubePlayer(BlockPos c, Vec3d p) {
        // treat player position in block coords (still feels correct)
        int px = (int)Math.floor(p.x);
        int py = (int)Math.floor(p.y);
        int pz = (int)Math.floor(p.z);
        return Math.abs(px - c.getX()) <= HALF_BLOCKS
                && Math.abs(py - c.getY()) <= HALF_BLOCKS
                && Math.abs(pz - c.getZ()) <= HALF_BLOCKS;
    }

    /** Optional: only boost for "normal" blocks (avoid bedrock etc.) */
    public static boolean canDismantle(BlockState state, net.minecraft.world.BlockView world, BlockPos pos) {
        float h = state.getHardness(world, pos);
        return h >= 0.0f; // unbreakable blocks usually return -1
    }
}
