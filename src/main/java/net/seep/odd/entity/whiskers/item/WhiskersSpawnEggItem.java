// FILE: src/main/java/net/seep/odd/entity/whiskers/item/WhiskersSpawnEggItem.java
package net.seep.odd.entity.whiskers.item;

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
import net.seep.odd.entity.whiskers.WhiskersEntity;

public final class WhiskersSpawnEggItem extends Item {

    public WhiskersSpawnEggItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        if (world.isClient) return ActionResult.SUCCESS;

        BlockPos pos = context.getBlockPos();
        Direction side = context.getSide();
        BlockState state = world.getBlockState(pos);

        // Spawn on the face you clicked (or inside if non-solid)
        BlockPos spawnPos = state.getCollisionShape(world, pos).isEmpty() ? pos : pos.offset(side);

        WhiskersEntity whiskers = ModEntities.WHISKERS.create(world);
        if (whiskers == null) return ActionResult.FAIL;

        whiskers.refreshPositionAndAngles(
                spawnPos.getX() + 0.5,
                spawnPos.getY(),
                spawnPos.getZ() + 0.5,
                MathHelper.wrapDegrees(world.getRandom().nextFloat() * 360f),
                0f
        );

        if (context.getPlayer() != null) {
            whiskers.setOwner(context.getPlayer());
            whiskers.setTamed(true);

            // start standing
            whiskers.setSitting(false);
            whiskers.setInSittingPose(false);
        }

        whiskers.setPersistent();
        whiskers.ensureVariantPicked(); // safe even if you only use 1 texture now
        world.spawnEntity(whiskers);

        ItemStack stack = context.getStack();
        if (context.getPlayer() == null || !context.getPlayer().getAbilities().creativeMode) {
            stack.decrement(1);
        }

        return ActionResult.CONSUME;
    }
}