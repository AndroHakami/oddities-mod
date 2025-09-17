package net.seep.odd.abilities.artificer.mixer;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;
import net.seep.odd.abilities.artificer.mixer.projectile.BrewBottleEntity;
import net.seep.odd.abilities.artificer.mixer.brew.BrewEffects;

import java.util.List;

public class ArtificerBrewItem extends Item {
    public enum Kind { DRINK, THROW }

    private static final int MAX_CHARGE_TICKS = 72000; // bow-style
    private final Kind kind;

    public ArtificerBrewItem(Settings settings, Kind kind) {
        super(settings);
        this.kind = kind;
    }

    /* ------------ Naming & tooltip from brewId ------------- */

    @Override
    public Text getName(ItemStack stack) {
        var nbt = stack.getNbt();
        if (nbt != null && nbt.contains("odd_brew_id")) {
            String id = nbt.getString("odd_brew_id");
            return Text.translatable("brew.odd." + id);
        }
        return super.getName(stack);
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext ctx) {
        var nbt = stack.getNbt();
        if (nbt != null && nbt.contains("odd_brew_id")) {
            String id = nbt.getString("odd_brew_id");
            tooltip.add(Text.translatable("brew.odd." + id + ".desc"));
        }
    }

    /* ---------------- Use behavior ---------------- */

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return kind == Kind.DRINK ? UseAction.DRINK : UseAction.BOW; // bow animation for throwables
    }

    @Override
    public int getMaxUseTime(ItemStack stack) {
        return kind == Kind.DRINK ? 24 : MAX_CHARGE_TICKS;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (kind == Kind.DRINK) {
            user.setCurrentHand(hand);
            return TypedActionResult.consume(stack);
        }
        // THROW: start charging
        user.setCurrentHand(hand);
        return TypedActionResult.consume(stack);
    }

    /** Called when player releases RMB (stop using). */
    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (kind != Kind.THROW || !(user instanceof PlayerEntity p)) return;

        int used = getMaxUseTime(stack) - remainingUseTicks;
        float power = getChargePower(used); // 0..1 (bow curve)
        if (power < 0.1f) return; // tiny taps do nothing

        if (!world.isClient) {
            // Spawn projectile that carries the NBT (brewId/color)
            BrewBottleEntity proj = new BrewBottleEntity(world, p, stack);
            // speed: base + scale with power; inaccuracy constant feels good
            float velocity = 0.6f + power * 1.6f;   // ~0.6 .. 2.2
            float inaccuracy = 0.25f;
            proj.setVelocity(p, p.getPitch(), p.getYaw(), 0.0F, velocity, inaccuracy);
            world.spawnEntity(proj);

            // Throw sound (prefer potion throw; fallback to snowball if missing)
            try {
                world.playSound(null, p.getBlockPos(),
                        SoundEvents.ENTITY_SPLASH_POTION_THROW, SoundCategory.PLAYERS,
                        0.8f, 0.9f + power * 0.3f);
            } catch (Throwable t) {
                world.playSound(null, p.getBlockPos(),
                        SoundEvents.ENTITY_SNOWBALL_THROW, SoundCategory.PLAYERS,
                        0.8f, 0.9f + power * 0.3f);
            }

            if (!p.getAbilities().creativeMode) stack.decrement(1);
            p.incrementStat(Stats.USED.getOrCreateStat(this));
        }
    }

    private static float getChargePower(int usedTicks) {
        // Same shape as bow: fast ramp then ease
        float x = usedTicks / 20.0f;
        x = (x * x + x * 2.0f) / 3.0f;
        return Math.min(x, 1.0f);
    }

    /* ------------- Drink behavior -------------- */

    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        if (!world.isClient && kind == Kind.DRINK) {
            String brewId = stack.getOrCreateNbt().getString("odd_brew_id");
            BrewEffects.applyDrink(world, user, brewId, stack);
            if (user instanceof PlayerEntity p && !p.getAbilities().creativeMode) stack.decrement(1);
        }
        return stack;
    }
}
