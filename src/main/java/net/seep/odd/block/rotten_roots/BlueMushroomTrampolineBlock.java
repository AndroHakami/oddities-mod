package net.seep.odd.block.rotten_roots;

import net.minecraft.block.BlockState;
import net.minecraft.block.MushroomBlock;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

public class BlueMushroomTrampolineBlock extends MushroomBlock {

    // Slime is basically 1.0x (inverts Y). This is stronger-than-slime.
    private static final double BOUNCE_MULTIPLIER = 2.2D;

    public BlueMushroomTrampolineBlock(Settings settings) {
        super(settings);
    }

    @Override
    public void onLandedUpon(World world, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
        // 0 damage multiplier = negate fall damage
        entity.handleFallDamage(fallDistance, 0.0F, world.getDamageSources().fall());
    }

    @Override
    public void onEntityLand(BlockView world, Entity entity) {
        // If sneaking, allow “normal landing” (no trampoline)
        if (entity.isSneaking() || entity.bypassesLandingEffects()) {
            super.onEntityLand(world, entity);
            return;
        }

        Vec3d v = entity.getVelocity();
        if (v.y < 0.0D) {
            entity.setVelocity(v.x, -v.y * BOUNCE_MULTIPLIER, v.z);
        } else {
            super.onEntityLand(world, entity);
        }
    }
}