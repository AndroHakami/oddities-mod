// FILE: src/main/java/net/seep/odd/entity/skull_bird/item/SkullBirdSpawnEggItem.java
package net.seep.odd.entity.skull_bird.item;

import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.seep.odd.entity.ModEntities;
import net.seep.odd.entity.skull_bird.SkullBirdEntity;

public final class SkullBirdSpawnEggItem extends Item {

    public SkullBirdSpawnEggItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        if (world.isClient) return ActionResult.SUCCESS;

        BlockPos pos = context.getBlockPos();
        Direction side = context.getSide();
        BlockState state = world.getBlockState(pos);

        BlockPos spawnPos = state.getCollisionShape(world, pos).isEmpty() ? pos : pos.offset(side);

        SkullBirdEntity bird = ModEntities.SKULL_BIRD.create(world);
        if (bird == null) return ActionResult.FAIL;

        bird.refreshPositionAndAngles(
                spawnPos.getX() + 0.5,
                spawnPos.getY(),
                spawnPos.getZ() + 0.5,
                MathHelper.wrapDegrees(world.getRandom().nextFloat() * 360f),
                0f
        );

        if (context.getPlayer() != null) {
            bird.setOwner(context.getPlayer());
            bird.setTamed(true);
            bird.setSitting(false);
            bird.setInSittingPose(false);
        }

        bird.ensureVariantPicked();
        bird.setPersistent();
        world.spawnEntity(bird);

        ItemStack stack = context.getStack();
        if (context.getPlayer() == null || !context.getPlayer().getAbilities().creativeMode) {
            stack.decrement(1);
        }

        return ActionResult.CONSUME;
    }
}