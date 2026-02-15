package net.seep.odd.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import net.seep.odd.item.ModItems;

public final class DabloonBookshelfBlock extends Block {
    public DabloonBookshelfBlock(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos,
                              PlayerEntity player, Hand hand, BlockHitResult hit) {
        // prevent offhand double-trigger
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;

        // client: just animate hand
        if (world.isClient) return ActionResult.SUCCESS;

        // safety: spectators shouldn't farm it
        if (player.isSpectator()) return ActionResult.PASS;

        // roll 3..5
        int count = 3 + world.random.nextInt(3);

        // spawn item entity
        ItemStack stack = new ItemStack(ModItems.DABLOON, count); // <-- must exist
        Vec3d spawn = Vec3d.ofCenter(pos).add(0.0, 0.35, 0.0);
        ItemEntity ent = new ItemEntity(world, spawn.x, spawn.y, spawn.z, stack);
        ent.setToDefaultPickupDelay();
        world.spawnEntity(ent);

        // swap to used bookshelf
        world.setBlockState(pos, ModBlocks.USED_DABLOON_BOOKSHELF.getDefaultState(), 3);

        // nice "reward" sound (swap to your own later if you want)
        world.playSound(
                null,
                pos,
                SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
                SoundCategory.BLOCKS,
                0.85f,
                0.95f + world.random.nextFloat() * 0.15f
        );

        return ActionResult.SUCCESS;
    }
}
