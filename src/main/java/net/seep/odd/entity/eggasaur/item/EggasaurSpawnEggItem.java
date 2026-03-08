package net.seep.odd.entity.eggasaur.item;

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
import net.seep.odd.entity.eggasaur.EggasaurEntity;

public final class EggasaurSpawnEggItem extends Item {

    public EggasaurSpawnEggItem(Settings settings) {
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

        EggasaurEntity eggasaur = ModEntities.EGGASAUR.create(world);
        if (eggasaur == null) return ActionResult.FAIL;

        eggasaur.refreshPositionAndAngles(
                spawnPos.getX() + 0.5,
                spawnPos.getY(),
                spawnPos.getZ() + 0.5,
                MathHelper.wrapDegrees(world.getRandom().nextFloat() * 360f),
                0f
        );

        if (context.getPlayer() != null) {
            eggasaur.setOwner(context.getPlayer());
            eggasaur.setTamed(true);
            eggasaur.setSitting(false);
            eggasaur.setInSittingPose(false);
        }

        eggasaur.setPersistent();
        eggasaur.ensureVariantPicked();
        world.spawnEntity(eggasaur);

        ItemStack stack = context.getStack();
        if (context.getPlayer() == null || !context.getPlayer().getAbilities().creativeMode) {
            stack.decrement(1);
        }

        return ActionResult.CONSUME;
    }
}