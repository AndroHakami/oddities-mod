package net.seep.odd.abilities.artificer.mixer;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;
import net.seep.odd.abilities.artificer.mixer.brew.BrewEffects;

public class ArtificerBrewItem extends Item {
    public enum Kind { DRINK, THROW }

    private final Kind kind;

    public ArtificerBrewItem(Settings settings, Kind kind) {
        super(settings);
        this.kind = kind;
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return kind == Kind.DRINK ? UseAction.DRINK : UseAction.NONE;
    }

    @Override
    public int getMaxUseTime(ItemStack stack) {
        return kind == Kind.DRINK ? 24 : 0;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (kind == Kind.DRINK) {
            user.setCurrentHand(hand);
            return TypedActionResult.consume(stack);
        }

        // THROWABLE: spawn projectile that carries this stack's NBT
        if (!world.isClient) {
            var proj = new net.seep.odd.abilities.artificer.mixer.projectile.BrewBottleEntity(world, user, stack);
            proj.setVelocity(user, user.getPitch(), user.getYaw(), 0.0F, 1.1F, 0.2F);
            world.spawnEntity(proj);
            if (!user.getAbilities().creativeMode) stack.decrement(1);
            user.incrementStat(Stats.USED.getOrCreateStat(this));
        } else {
            world.playSound(user, user.getBlockPos(), SoundEvents.ENTITY_SNOWBALL_THROW, SoundCategory.PLAYERS, 0.8f, 1.2f);
        }
        return TypedActionResult.success(stack, world.isClient);
    }

    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        if (!world.isClient && kind == Kind.DRINK) {
            String brewId = stack.getOrCreateNbt().getString("odd_brew_id");
            BrewEffects.applyDrink(world, user, brewId, stack);
            if (user instanceof PlayerEntity p && !p.getAbilities().creativeMode) {
                stack.decrement(1);
            }
        }
        return stack;
    }
}
