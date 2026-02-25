// FILE: src/main/java/net/seep/odd/abilities/artificer/mixer/brew/BrittleEffect.java
package net.seep.odd.abilities.artificer.mixer.brew;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public final class BrittleEffect {
    private BrittleEffect() {}

    public static final int RADIUS = 5;

    public static void apply(World world, BlockPos pos, @Nullable LivingEntity thrower, net.minecraft.item.ItemStack stack) {
        if (!(world instanceof ServerWorld sw)) return;

        BlockPos.Mutable m = new BlockPos.Mutable();
        int r = RADIUS;
        int r2 = r * r;

        // sphere-ish (looks nicer)
        for (int dx = -r; dx <= r; dx++)
            for (int dy = -r; dy <= r; dy++)
                for (int dz = -r; dz <= r; dz++) {
                    if (dx*dx + dy*dy + dz*dz > r2) continue;

                    m.set(pos.getX()+dx, pos.getY()+dy, pos.getZ()+dz);
                    BlockState st = sw.getBlockState(m);

                    if (!isBrittleAllowed(sw, m, st)) continue;

                    BlockState out = pickGlass(sw, m, st);
                    if (out == null) continue;

                    sw.setBlockState(m, out, net.minecraft.block.Block.NOTIFY_ALL);

                    // tiny sparkle
                    if (sw.getRandom().nextInt(6) == 0) {
                        sw.spawnParticles(net.minecraft.particle.ParticleTypes.END_ROD,
                                m.getX()+0.5, m.getY()+0.8, m.getZ()+0.5,
                                1, 0.08, 0.12, 0.08, 0.01);
                    }
                }

        sw.playSound(null, pos, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.BLOCKS, 1.0f, 1.0f);
    }

    private static boolean isBrittleAllowed(ServerWorld sw, BlockPos pos, BlockState st) {
        if (st.isAir()) return false;
        if (!st.getFluidState().isEmpty()) return false;

        // unbreakables
        if (st.getHardness(sw, pos) < 0) return false;

        // skip functional / block entities
        if (st.hasBlockEntity()) return false;

        // skip doors + redstone-y interactables
        if (st.isIn(BlockTags.DOORS)) return false;
        if (st.isIn(BlockTags.TRAPDOORS)) return false;
        if (st.isIn(BlockTags.BUTTONS)) return false;
        if (st.isIn(BlockTags.PRESSURE_PLATES)) return false;

        // skip ores (broad filter)
        String path = Registries.BLOCK.getId(st.getBlock()).getPath();
        if (path.contains("ore")) return false;

        // skip common functional blocks by name (covers chests, crafting, etc.)
        if (path.contains("chest") || path.contains("barrel") || path.contains("furnace") || path.contains("smoker")
                || path.contains("blast_furnace") || path.contains("brewing") || path.contains("enchant")
                || path.contains("smith") || path.contains("anvil") || path.contains("grindstone")
                || path.contains("stonecutter") || path.contains("cartography") || path.contains("loom")
                || path.contains("fletching") || path.contains("hopper") || path.contains("dispenser")
                || path.contains("dropper") || path.contains("observer") || path.contains("piston")
                || path.contains("note_block") || path.contains("jukebox")) return false;

        // skip bedrock/barrier
        return !st.isOf(Blocks.BEDROCK) && !st.isOf(Blocks.BARRIER);
    }

    private static BlockState pickGlass(ServerWorld sw, BlockPos pos, BlockState st) {
        // explicit “Creation-like” vibe overrides (your examples)
        if (st.isOf(Blocks.GRASS_BLOCK) || st.isOf(Blocks.FERN) || st.isOf(Blocks.TALL_GRASS))
            return Blocks.GREEN_STAINED_GLASS.getDefaultState();

        if (st.isIn(BlockTags.SAND)) {
            if (st.isOf(Blocks.RED_SAND)) return Blocks.ORANGE_STAINED_GLASS.getDefaultState();
            return Blocks.YELLOW_STAINED_GLASS.getDefaultState();
        }

        if (st.isIn(BlockTags.LEAVES)) return Blocks.LIME_STAINED_GLASS.getDefaultState();
        if (st.isOf(Blocks.SNOW_BLOCK) || st.isOf(Blocks.SNOW) || st.isOf(Blocks.POWDER_SNOW))
            return Blocks.WHITE_STAINED_GLASS.getDefaultState();

        if (st.isOf(Blocks.ICE) || st.isOf(Blocks.PACKED_ICE) || st.isOf(Blocks.BLUE_ICE))
            return Blocks.LIGHT_BLUE_STAINED_GLASS.getDefaultState();

        // general case: MapColor -> nearest DyeColor
        int rgb = st.getMapColor(sw, pos).color;
        int r = (rgb >> 16) & 255;
        int g = (rgb >> 8) & 255;
        int b = rgb & 255;

        // very dark -> tinted glass
        if (r + g + b < 90) return Blocks.TINTED_GLASS.getDefaultState();
        // very bright -> plain glass
        if (r + g + b > 650) return Blocks.GLASS.getDefaultState();

        DyeColor dye = nearestDye(r, g, b);
        return stainedGlassOf(dye);
    }

    private static DyeColor nearestDye(int r, int g, int b) {
        DyeColor best = DyeColor.WHITE;
        double bestD = Double.MAX_VALUE;

        for (DyeColor dc : DyeColor.values()) {
            int c = dc.getFireworkColor();
            int dr = ((c >> 16) & 255) - r;
            int dg = ((c >> 8) & 255) - g;
            int db = (c & 255) - b;
            double d = dr*dr + dg*dg + db*db;
            if (d < bestD) { bestD = d; best = dc; }
        }
        return best;
    }

    private static BlockState stainedGlassOf(DyeColor dye) {
        return switch (dye) {
            case WHITE -> Blocks.WHITE_STAINED_GLASS.getDefaultState();
            case ORANGE -> Blocks.ORANGE_STAINED_GLASS.getDefaultState();
            case MAGENTA -> Blocks.MAGENTA_STAINED_GLASS.getDefaultState();
            case LIGHT_BLUE -> Blocks.LIGHT_BLUE_STAINED_GLASS.getDefaultState();
            case YELLOW -> Blocks.YELLOW_STAINED_GLASS.getDefaultState();
            case LIME -> Blocks.LIME_STAINED_GLASS.getDefaultState();
            case PINK -> Blocks.PINK_STAINED_GLASS.getDefaultState();
            case GRAY -> Blocks.GRAY_STAINED_GLASS.getDefaultState();
            case LIGHT_GRAY -> Blocks.LIGHT_GRAY_STAINED_GLASS.getDefaultState();
            case CYAN -> Blocks.CYAN_STAINED_GLASS.getDefaultState();
            case PURPLE -> Blocks.PURPLE_STAINED_GLASS.getDefaultState();
            case BLUE -> Blocks.BLUE_STAINED_GLASS.getDefaultState();
            case BROWN -> Blocks.BROWN_STAINED_GLASS.getDefaultState();
            case GREEN -> Blocks.GREEN_STAINED_GLASS.getDefaultState();
            case RED -> Blocks.RED_STAINED_GLASS.getDefaultState();
            case BLACK -> Blocks.BLACK_STAINED_GLASS.getDefaultState();
        };
    }
}
