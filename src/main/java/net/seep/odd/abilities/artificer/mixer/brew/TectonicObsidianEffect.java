package net.seep.odd.abilities.artificer.mixer.brew;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public final class TectonicObsidianEffect {
    private TectonicObsidianEffect() {}

    public static void apply(World world, BlockPos hitPos, @Nullable LivingEntity thrower, net.minecraft.item.ItemStack stack) {
        if (!(world instanceof ServerWorld sw)) return;

        Random rng = sw.getRandom();

        // ✅ smaller radius (was 5..8) -> now 2..3 (≈ “less by ~5 blocks”)
        // If you want slightly bigger, change to: 3 + rng.nextInt(2) => 3..4
        int radius = 2 + rng.nextInt(2); // 2,3
        int depthMax = 30;

        // find a reasonable “ground start” Y near hit (if it landed in air)
        BlockPos center = findGround(sw, hitPos);

        carveCrater(sw, center, radius, depthMax, rng);

        sw.playSound(null, center, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 1.2f, 0.75f);
        sw.playSound(null, center, SoundEvents.BLOCK_NETHERITE_BLOCK_PLACE, SoundCategory.BLOCKS, 0.9f, 0.6f + rng.nextFloat() * 0.2f);
    }

    private static BlockPos findGround(ServerWorld w, BlockPos p) {
        BlockPos.Mutable m = p.mutableCopy();

        // if in air, scan downward a bit
        for (int i = 0; i < 10; i++) {
            if (!w.getBlockState(m).isAir()) break;
            m.setY(m.getY() - 1);
            if (m.getY() <= w.getBottomY() + 1) break;
        }
        return m.toImmutable();
    }

    private static void carveCrater(ServerWorld w, BlockPos center, int radius, int depthMax, Random rng) {
        int cx = center.getX();
        int cy = center.getY();
        int cz = center.getZ();

        int r = radius;
        int rSq = r * r;

        BlockPos.Mutable m = new BlockPos.Mutable();

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int distSq = dx * dx + dz * dz;
                if (distSq > rSq) continue;

                float dist = (float) Math.sqrt(distSq);
                float t = dist / (float) r;

                // deeper at center, shallower at edges, plus some noise
                float profile = 1.0f - (float) Math.pow(t, 1.65);
                int localDepth = MathHelper.clamp((int) (depthMax * profile + rng.nextInt(4) - 1), 4, depthMax);

                boolean edgeZone = dist >= (r - 1.0f);

                int x = cx + dx;
                int z = cz + dz;

                boolean hitAirGap = false;

                for (int d = 0; d <= localDepth; d++) {
                    int y = cy - d;
                    if (y <= w.getBottomY() + 1) break;

                    m.set(x, y, z);

                    BlockState bs = w.getBlockState(m);

                    // ✅ If we reach air, STOP going downward for this column.
                    // This makes the crater NOT “grow” into caves/void pockets.
                    if (bs.isAir()) {
                        hitAirGap = true;
                        break;
                    }
                    if (hitAirGap) break;

                    // never touch block entities or protected stuff
                    if (w.getBlockEntity(m) != null) continue;
                    if (!isSafeBreakable(w, m, bs)) continue;

                    // edges become obsidian (only when replacing non-air; we already break on air)
                    if (edgeZone && (rng.nextFloat() < 0.90f || d > localDepth * 0.35f)) {
                        w.setBlockState(m, Blocks.OBSIDIAN.getDefaultState(), Block.NOTIFY_ALL);
                    } else {
                        // carve air
                        w.setBlockState(m, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                    }
                }
            }
        }
    }

    /**
     * “Ground-only” break policy:
     * - Only replace common terrain stuff.
     * - No containers/doors/etc (block entities already skipped; doors/etc filtered here).
     */
    private static boolean isSafeBreakable(ServerWorld w, BlockPos pos, BlockState bs) {
        Block b = bs.getBlock();

        if (b == Blocks.BEDROCK) return false;
        if (b == Blocks.OBSIDIAN) return false; // leave existing obsidian
        if (b == Blocks.CRYING_OBSIDIAN) return false;
        if (b == Blocks.REINFORCED_DEEPSLATE) return false;

        // avoid “valuable / interactable”
        if (bs.isIn(net.minecraft.registry.tag.BlockTags.DOORS)) return false;
        if (bs.isIn(net.minecraft.registry.tag.BlockTags.TRAPDOORS)) return false;
        if (bs.isIn(net.minecraft.registry.tag.BlockTags.BUTTONS)) return false;
        if (bs.isIn(net.minecraft.registry.tag.BlockTags.PRESSURE_PLATES)) return false;
        if (bs.isIn(net.minecraft.registry.tag.BlockTags.FENCES)) return false;
        if (bs.isIn(net.minecraft.registry.tag.BlockTags.FENCE_GATES)) return false;
        if (bs.isIn(net.minecraft.registry.tag.BlockTags.SIGNS)) return false;

        // allow common terrain + simple wood solids (logs/planks), NOT “everything wood”
        if (bs.isIn(net.minecraft.registry.tag.BlockTags.BASE_STONE_OVERWORLD)) return true;
        if (bs.isIn(net.minecraft.registry.tag.BlockTags.DIRT)) return true;
        if (bs.isOf(Blocks.SAND) || bs.isOf(Blocks.RED_SAND)) return true;
        if (bs.isOf(Blocks.GRAVEL) || bs.isOf(Blocks.CLAY)) return true;
        if (bs.isOf(Blocks.NETHERRACK) || bs.isOf(Blocks.BLACKSTONE) || bs.isOf(Blocks.BASALT)) return true;
        if (bs.isIn(net.minecraft.registry.tag.BlockTags.LOGS)) return true;
        if (bs.isIn(net.minecraft.registry.tag.BlockTags.PLANKS)) return true;

        // allow simple stone variants
        if (bs.isOf(Blocks.COBBLESTONE) || bs.isOf(Blocks.STONE) || bs.isOf(Blocks.DEEPSLATE)) return true;
        if (bs.isOf(Blocks.ANDESITE) || bs.isOf(Blocks.DIORITE) || bs.isOf(Blocks.GRANITE)) return true;
        if (bs.isOf(Blocks.TUFF) || bs.isOf(Blocks.CALCITE)) return true;

        return false;
    }
}
