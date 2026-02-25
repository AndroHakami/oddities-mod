// FILE: src/main/java/net/seep/odd/abilities/artificer/mixer/brew/GeoFrostEffect.java
package net.seep.odd.abilities.artificer.mixer.brew;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public final class GeoFrostEffect {
    private GeoFrostEffect() {}

    public static final int RADIUS = 7;

    // “generally generated” blocks -> icy variants
    private static final Map<Block, BlockState> SWAP = new HashMap<>();
    static {
        // dirt family
        SWAP.put(Blocks.DIRT, Blocks.PACKED_ICE.getDefaultState());
        SWAP.put(Blocks.GRASS_BLOCK, Blocks.PACKED_ICE.getDefaultState());
        SWAP.put(Blocks.COARSE_DIRT, Blocks.PACKED_ICE.getDefaultState());
        SWAP.put(Blocks.PODZOL, Blocks.PACKED_ICE.getDefaultState());
        SWAP.put(Blocks.MYCELIUM, Blocks.PACKED_ICE.getDefaultState());
        SWAP.put(Blocks.ROOTED_DIRT, Blocks.PACKED_ICE.getDefaultState());
        SWAP.put(Blocks.MOSS_BLOCK, Blocks.PACKED_ICE.getDefaultState());

        // stone-ish
        SWAP.put(Blocks.STONE, Blocks.BLUE_ICE.getDefaultState());
        SWAP.put(Blocks.COBBLESTONE, Blocks.PACKED_ICE.getDefaultState());
        SWAP.put(Blocks.ANDESITE, Blocks.BLUE_ICE.getDefaultState());
        SWAP.put(Blocks.DIORITE, Blocks.BLUE_ICE.getDefaultState());
        SWAP.put(Blocks.GRANITE, Blocks.BLUE_ICE.getDefaultState());
        SWAP.put(Blocks.DEEPSLATE, Blocks.PACKED_ICE.getDefaultState());
        SWAP.put(Blocks.COBBLED_DEEPSLATE, Blocks.PACKED_ICE.getDefaultState());

        // sand/gravel
        SWAP.put(Blocks.SAND, Blocks.SNOW_BLOCK.getDefaultState());
        SWAP.put(Blocks.RED_SAND, Blocks.SNOW_BLOCK.getDefaultState());
        SWAP.put(Blocks.GRAVEL, Blocks.PACKED_ICE.getDefaultState());

        // ice interactions
        SWAP.put(Blocks.WATER, Blocks.ICE.getDefaultState());
    }

    public static void apply(World world, BlockPos impactPos, @Nullable LivingEntity thrower, ItemStack stack) {
        if (!(world instanceof ServerWorld sw)) return;

        BlockPos.Mutable m = new BlockPos.Mutable();

        int r = RADIUS;
        int r2 = r * r;

        // Circle radius 7, with a small vertical range so it “feels” like area impact
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx*dx + dz*dz > r2) continue;

                    m.set(impactPos.getX() + dx, impactPos.getY() + dy, impactPos.getZ() + dz);

                    BlockState st = sw.getBlockState(m);
                    Block b = st.getBlock();

                    // skip air / unbreakables / containers
                    if (st.isAir()) continue;
                    if (st.getHardness(sw, m) < 0) continue;

                    BlockState out = SWAP.get(b);

                    // water (any water block state)
                    if (out == null && st.isOf(Blocks.WATER)) out = Blocks.ICE.getDefaultState();

                    // dirt/stone tags fallback
                    if (out == null) {
                        if (st.isIn(BlockTags.DIRT)) out = Blocks.PACKED_ICE.getDefaultState();
                        else if (st.isIn(BlockTags.STONE_ORE_REPLACEABLES) || st.isIn(BlockTags.BASE_STONE_OVERWORLD))
                            out = Blocks.BLUE_ICE.getDefaultState();
                    }

                    if (out == null) continue;

                    // don’t spam-replace already icy
                    if (st.isOf(out.getBlock())) continue;

                    sw.setBlockState(m, out, Block.NOTIFY_ALL);
                    sw.spawnParticles(net.minecraft.particle.ParticleTypes.SNOWFLAKE,
                            m.getX() + 0.5, m.getY() + 0.8, m.getZ() + 0.5,
                            2, 0.18, 0.18, 0.18, 0.02);
                }
            }
        }

        sw.playSound(null, impactPos, SoundEvents.BLOCK_GLASS_PLACE, SoundCategory.BLOCKS, 0.8f, 1.35f);
        sw.playSound(null, impactPos, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 0.5f, 1.9f);
    }
}
