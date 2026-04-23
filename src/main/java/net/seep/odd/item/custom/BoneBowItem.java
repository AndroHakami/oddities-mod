package net.seep.odd.item.custom;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.MathHelper;

public final class BoneBowItem extends BowItem {
    private static final float MELEE_KNOCKBACK = 1.9F;

    public BoneBowItem(Settings settings) {
        super(settings);
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.BOW;
    }

    @Override
    public int getMaxUseTime(ItemStack stack) {
        return 72000;
    }

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!attacker.getWorld().isClient()) {
            float yawRad = attacker.getYaw() * 0.017453292F;

            target.takeKnockback(
                    MELEE_KNOCKBACK,
                    MathHelper.sin(yawRad),
                    -MathHelper.cos(yawRad)
            );
            target.addVelocity(0.0D, 0.12D, 0.0D);

            attacker.getWorld().playSound(
                    null,
                    target.getX(), target.getY(), target.getZ(),
                    SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP,
                    SoundCategory.PLAYERS,
                    0.9F,
                    0.95F + attacker.getRandom().nextFloat() * 0.1F
            );
        }

        EquipmentSlot slot = attacker.getMainHandStack() == stack
                ? EquipmentSlot.MAINHAND
                : EquipmentSlot.OFFHAND;

        stack.damage(1, attacker, entity -> entity.sendEquipmentBreakStatus(slot));
        return true;
    }
}