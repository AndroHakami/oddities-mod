package net.seep.odd.abilities.artificer.mixer.brew;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public final class GeologicPulseEffect {
    private GeologicPulseEffect() {}

    // tweak these
    public static final int DEFAULT_RADIUS = 3;     // 3 = nice medium burst
    private static final int MAX_CHANGES = 220;     // safety cap so it can't nuke chunks

    public static void apply(World world, BlockPos pos, @Nullable LivingEntity thrower, ItemStack stack) {
        if (!(world instanceof ServerWorld sw)) return;

        int r = DEFAULT_RADIUS;
        int changed = 0;

        // big center burst
        sw.spawnParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5,
                90, 0.55, 0.35, 0.55, 0.02);
        sw.spawnParticles(ParticleTypes.LAVA, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5,
                18, 0.35, 0.25, 0.35, 0.02);
        sw.spawnParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5,
                26, 0.45, 0.25, 0.45, 0.01);

        sw.playSound(null, pos, SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.BLOCKS,
                1.0f, 0.85f + sw.getRandom().nextFloat() * 0.25f);

        BlockPos.Mutable m = new BlockPos.Mutable();

        for (int dy = -r; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {

                    // spherical-ish cutoff
                    if (dx*dx + dy*dy + dz*dz > r*r) continue;

                    m.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);

                    BlockState state = sw.getBlockState(m);
                    if (!canReplace(sw, m, state)) continue;

                    BlockState hot = hotterFor(sw, state);
                    if (hot == null || hot == state) continue;

                    sw.setBlockState(m, hot, Block.NOTIFY_ALL);
                    changed++;

                    // fiery effect on changed blocks (but not too spammy)
                    if (changed <= 80 || sw.getRandom().nextFloat() < 0.35f) {
                        double px = m.getX() + 0.5;
                        double py = m.getY() + 0.6;
                        double pz = m.getZ() + 0.5;

                        int flames = 3 + sw.getRandom().nextInt(3); // 3..5
                        sw.spawnParticles(ParticleTypes.FLAME, px, py, pz, flames, 0.18, 0.18, 0.18, 0.01);
                        sw.spawnParticles(ParticleTypes.SMOKE, px, py, pz, 2, 0.14, 0.14, 0.14, 0.01);

                        if (sw.getRandom().nextFloat() < 0.20f) {
                            sw.spawnParticles(ParticleTypes.LAVA, px, py, pz, 1, 0.10, 0.10, 0.10, 0.01);
                        }
                    }

                    if (changed >= MAX_CHANGES) return;
                }
            }
        }

        // subtle “after” pop
        float pitch = MathHelper.clamp(0.95f + sw.getRandom().nextFloat() * 0.2f, 0.85f, 1.25f);
        sw.playSound(null, pos, SoundEvents.BLOCK_LAVA_POP, SoundCategory.BLOCKS, 0.8f, pitch);
    }

    private static boolean canReplace(ServerWorld sw, BlockPos pos, BlockState state) {
        if (state.isAir()) return false;
        if (!state.getFluidState().isEmpty()) return false;
        if (state.hasBlockEntity()) return false;

        // explicitly forbidden
        if (state.isOf(Blocks.OBSIDIAN) || state.isOf(Blocks.CRYING_OBSIDIAN)) return false;
        if (state.isOf(Blocks.BEDROCK)) return false;

        // don’t bother changing stuff that’s already “hot”
        if (state.isOf(Blocks.MAGMA_BLOCK) || state.isOf(Blocks.BASALT) || state.isOf(Blocks.BLACKSTONE) || state.isOf(Blocks.NETHERRACK)) return false;

        return isSoil(state) || isSandish(state) || isStoneish(state) || isGravelish(state);
    }

    private static BlockState hotterFor(ServerWorld sw, BlockState state) {
        // dirt / sand / gravel -> magma (fiery)
        if (isSoil(state) || isSandish(state) || isGravelish(state)) {
            // tiny variety so it doesn’t look too uniform
            return sw.getRandom().nextFloat() < 0.12f
                    ? Blocks.NETHERRACK.getDefaultState()
                    : Blocks.MAGMA_BLOCK.getDefaultState();
        }

        // stone-ish -> basalt (volcanic rock), occasionally blackstone
        if (isStoneish(state)) {
            float f = sw.getRandom().nextFloat();
            if (f < 0.18f) return Blocks.BLACKSTONE.getDefaultState();
            return Blocks.BASALT.getDefaultState();
        }

        return null;
    }

    private static boolean isSoil(BlockState state) {
        return state.isIn(BlockTags.DIRT)
                || state.isOf(Blocks.GRASS_BLOCK)
                || state.isOf(Blocks.MYCELIUM)
                || state.isOf(Blocks.PODZOL)
                || state.isOf(Blocks.ROOTED_DIRT)
                || state.isOf(Blocks.MUD)
                || state.isOf(Blocks.CLAY)
                || state.isOf(Blocks.FARMLAND)
                || state.isOf(Blocks.DIRT_PATH);
    }

    private static boolean isSandish(BlockState state) {
        return state.isIn(BlockTags.SAND)
                || state.isOf(Blocks.SANDSTONE)
                || state.isOf(Blocks.RED_SANDSTONE)
                || state.isOf(Blocks.CUT_SANDSTONE)
                || state.isOf(Blocks.CUT_RED_SANDSTONE)
                || state.isOf(Blocks.SMOOTH_SANDSTONE)
                || state.isOf(Blocks.SMOOTH_RED_SANDSTONE);
    }

    private static boolean isGravelish(BlockState state) {
        return state.isOf(Blocks.GRAVEL);
    }

    private static boolean isStoneish(BlockState state) {
        // broad but safe “stone family”
        return state.isIn(BlockTags.BASE_STONE_OVERWORLD)
                || state.isIn(BlockTags.STONE_ORE_REPLACEABLES)
                || state.isIn(BlockTags.DEEPSLATE_ORE_REPLACEABLES)
                || state.isOf(Blocks.COBBLESTONE)
                || state.isOf(Blocks.DEEPSLATE)
                || state.isOf(Blocks.COBBLED_DEEPSLATE)
                || state.isOf(Blocks.ANDESITE)
                || state.isOf(Blocks.DIORITE)
                || state.isOf(Blocks.GRANITE)
                || state.isOf(Blocks.TUFF);
    }
}
