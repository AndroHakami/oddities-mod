package net.seep.odd.abilities.artificer.mixer;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsage;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

public class ArtificerBrewItem extends Item {
    public enum Kind { DRINK, THROW }
    private final Kind kind;

    public ArtificerBrewItem(Settings s, Kind k) { super(s); this.kind = k; }

    @Override public UseAction getUseAction(ItemStack stack) { return kind == Kind.DRINK ? UseAction.DRINK : UseAction.NONE; }
    @Override public int getMaxUseTime(ItemStack stack) { return kind == Kind.DRINK ? 32 : 0; }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (kind == Kind.DRINK) {
            return ItemUsage.consumeHeldItem(world, user, hand);
        } else {
            // THROW: play a sound + (later) spawn your projectile
            if (!world.isClient) {
                world.playSound(null, user.getBlockPos(),
                        SoundEvents.ENTITY_SNOWBALL_THROW, SoundCategory.PLAYERS, 0.8f, 1.2f);
                if (!user.getAbilities().creativeMode) {
                    stack.decrement(1);
                }
            }
            user.incrementStat(Stats.USED.getOrCreateStat(this));
            return TypedActionResult.success(stack, world.isClient);
        }
    }

    @Override
    public ItemStack finishUsing(ItemStack stack, World world, net.minecraft.entity.LivingEntity user) {
        if (!world.isClient) {
            // TODO: apply brew effects via NBT (e.g., stack.getOrCreateNbt().getString("odd_brew_id"))
            user.playSound(SoundEvents.ENTITY_WITCH_DRINK, 0.8f, 1.6f);
            if (user instanceof PlayerEntity p && !p.getAbilities().creativeMode) {
                stack.decrement(1);
            }
        }
        return stack;
    }
}
