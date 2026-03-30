package net.seep.odd.item.custom;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import net.seep.odd.entity.projectile.OceanChakramEntity;

public class OceanChakramItem extends SwordItem {
    private static final int THROW_COOLDOWN_TICKS = 8;

    public OceanChakramItem(ToolMaterial material, int attackDamage, float attackSpeed, Settings settings) {
        super(material, attackDamage, attackSpeed, settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (user.getItemCooldownManager().isCoolingDown(this)) {
            return TypedActionResult.fail(stack);
        }

        if (!world.isClient) {
            boolean alreadyThrown = !world.getEntitiesByClass(
                    OceanChakramEntity.class,
                    user.getBoundingBox().expand(128.0D),
                    entity -> entity.getOwner() == user && !entity.isRemoved()
            ).isEmpty();

            if (alreadyThrown) {
                return TypedActionResult.fail(stack);
            }

            OceanChakramEntity chakram = new OceanChakramEntity(world, user);
            chakram.setShouldReturnToInventory(!user.getAbilities().creativeMode);

            ItemStack thrownStack = stack.copy();
            thrownStack.setCount(1);
            chakram.setItem(thrownStack);

            chakram.refreshPositionAndAngles(
                    user.getX(),
                    user.getEyeY() - 0.10D,
                    user.getZ(),
                    user.getYaw(),
                    user.getPitch()
            );

            chakram.setVelocity(user, user.getPitch(), user.getYaw(), 0.0F, 1.8F, 0.35F);
            world.spawnEntity(chakram);

            world.playSound(
                    null,
                    user.getX(), user.getY(), user.getZ(),
                    SoundEvents.ITEM_TRIDENT_THROW,
                    SoundCategory.PLAYERS,
                    0.9F,
                    1.1F
            );

            if (!user.getAbilities().creativeMode) {
                stack.decrement(1);
            }
        }

        user.swingHand(hand, true);
        user.getItemCooldownManager().set(this, THROW_COOLDOWN_TICKS);
        return TypedActionResult.success(stack, world.isClient());
    }
}