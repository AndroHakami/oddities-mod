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
import net.seep.odd.lore.AtheneumLoreBooks;

public final class StarryBookshelfBlock extends Block {
    public StarryBookshelfBlock(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos,
                              PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (world.isClient) return ActionResult.SUCCESS;
        if (player.isSpectator()) return ActionResult.PASS;

        ItemStack stack = AtheneumLoreBooks.createRandomBook(world.random);
        Vec3d spawn = Vec3d.ofCenter(pos).add(0.0, 0.35, 0.0);
        ItemEntity ent = new ItemEntity(world, spawn.x, spawn.y, spawn.z, stack);
        ent.setToDefaultPickupDelay();
        world.spawnEntity(ent);

        world.setBlockState(pos, ModBlocks.USED_DABLOON_BOOKSHELF.getDefaultState(), 3);
        world.playSound(null, pos, SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.BLOCKS,
                0.95f, 0.95f + world.random.nextFloat() * 0.1f);
        return ActionResult.SUCCESS;
    }
}
