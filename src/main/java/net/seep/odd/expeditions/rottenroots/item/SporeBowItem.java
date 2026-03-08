// FILE: src/main/java/net/seep/odd/expeditions/rottenroots/item/SporeBowItem.java
package net.seep.odd.expeditions.rottenroots.item;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.RangedWeaponItem;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.stat.Stats;

import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

import net.seep.odd.Oddities;
import net.seep.odd.entity.ModEntities;
import net.seep.odd.entity.rotten_roots.SporeMushroomProjectileEntity;

import java.util.function.Predicate;

public final class SporeBowItem extends RangedWeaponItem {

    /**
     * Datapack-driven ammo tag:
     * data/odd/tags/items/spore_bow_ammo.json
     */
    public static final TagKey<Item> AMMO_TAG =
            TagKey.of(RegistryKeys.ITEM, new Identifier(Oddities.MOD_ID, "spore_bow_ammo"));

    private static final Predicate<ItemStack> AMMO_PRED = stack -> stack.isIn(AMMO_TAG);

    public SporeBowItem(Settings settings) {
        super(settings);
    }

    @Override
    public Predicate<ItemStack> getProjectiles() {
        return AMMO_PRED;
    }

    @Override
    public int getRange() {
        return 15;
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
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack bow = user.getStackInHand(hand);
        ItemStack ammo = user.getProjectileType(bow);

        if (!ammo.isEmpty() || user.getAbilities().creativeMode) {
            user.setCurrentHand(hand);
            return TypedActionResult.consume(bow);
        }
        return TypedActionResult.fail(bow);
    }

    @Override
    public void onStoppedUsing(ItemStack bow, World world, LivingEntity user, int remainingUseTicks) {
        if (!(user instanceof PlayerEntity player)) return;

        boolean creative = player.getAbilities().creativeMode;
        ItemStack ammo = player.getProjectileType(bow);

        if (ammo.isEmpty() && !creative) return;

        int usedTicks = this.getMaxUseTime(bow) - remainingUseTicks;
        float pull = getPullProgress(usedTicks);
        if ((double) pull < 0.1D) return;

        if (ammo.isEmpty()) {
            // creative fallback: default to red mushroom if no ammo stack
            ammo = new ItemStack(net.minecraft.item.Items.RED_MUSHROOM);
        }

        if (!world.isClient) {
            ItemStack shot = ammo.copy();
            shot.setCount(1);

            SporeMushroomProjectileEntity proj =
                    new SporeMushroomProjectileEntity(ModEntities.SPORE_MUSHROOM_PROJECTILE, world, player);
            proj.setShotStack(shot);

            // Bow-like velocity (same feel as vanilla bow)
            proj.setVelocity(player, player.getPitch(), player.getYaw(), 0.0F, pull * 3.0F, 1.0F);

            // Low damage, big knockback handled inside projectile
            world.spawnEntity(proj);
        }

        world.playSound(
                null,
                player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_ARROW_SHOOT,
                SoundCategory.PLAYERS,
                1.0F,
                1.0F / (world.getRandom().nextFloat() * 0.4F + 1.2F) + pull * 0.5F
        );

        // durability cost like bow
        bow.damage(1, player, p -> p.sendToolBreakStatus(player.getActiveHand()));

        // consume ammo (unless creative)
        if (!creative) {
            ammo.decrement(1);
            if (ammo.isEmpty()) {
                player.getInventory().removeOne(ammo);
            }
        }

        player.incrementStat(Stats.USED.getOrCreateStat(this));
    }

    // Same curve as vanilla bow
    public static float getPullProgress(int useTicks) {
        float f = (float) useTicks / 20.0F;
        f = (f * f + f * 2.0F) / 3.0F;
        if (f > 1.0F) f = 1.0F;
        return f;
    }
}