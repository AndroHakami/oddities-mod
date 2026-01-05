package net.seep.odd.abilities.conquer.item;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;

import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.particle.ParticleTypes;

import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

import net.seep.odd.abilities.power.ConquerPower;

import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.util.GeckoLibUtil;
import software.bernie.geckolib.core.animation.AnimatableManager;

import java.util.function.Consumer;
import java.util.function.Supplier;

public final class WinterScytheItem extends SwordItem implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /**
     * Diamond-sword-ish damage, but faster swing:
     * - Using ToolMaterials.DIAMOND
     * - attackDamage bonus set to 4 (commonly yields diamond sword total)
     * - attackSpeed increased (less negative = faster)
     */
    public WinterScytheItem(Settings settings) {
        super(ToolMaterials.DIAMOND, 4, -2.0f, settings);
    }

    // ---- Conquer-only lock ----
    private static boolean allowed(PlayerEntity player) {
        return ConquerPower.hasConquer(player);
    }


    public void inventoryTick(ItemStack stack, World world, LivingEntity entity, int slot, boolean selected) {
        super.inventoryTick(stack, world, entity, slot, selected);

        if (world.isClient) return;
        if (!(entity instanceof PlayerEntity player)) return;

        if (!allowed(player)) {
            // Hard deny: remove it from them (prevents “use” & effectively prevents keeping it)
            ItemStack copy = stack.copy();
            yankFromPlayer(player, stack);

            // Drop it so it can’t be “kept”
            player.dropItem(copy, true);

            // Optional message (throttled)
            if (player.age % 40 == 0) {
                player.sendMessage(Text.literal("The Winter Scythe rejects you."), true);
            }
        }
    }

    private static void yankFromPlayer(PlayerEntity player, ItemStack stack) {
        if (player.getMainHandStack() == stack) {
            player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
            return;
        }
        if (player.getOffHandStack() == stack) {
            player.setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY);
            return;
        }
        for (int i = 0; i < player.getInventory().size(); i++) {
            if (player.getInventory().getStack(i) == stack) {
                player.getInventory().setStack(i, ItemStack.EMPTY);
                return;
            }
        }
    }

    // ---- Lifesteal ----
    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean ok = super.postHit(stack, target, attacker);

        if (!attacker.getWorld().isClient && attacker instanceof PlayerEntity player) {
            if (!allowed(player)) return ok;

            double atk = player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
            float heal = (float) Math.min(6.0D, Math.max(1.0D, atk * 0.25D)); // 1..6 hp

            player.heal(heal);

            if (player.getWorld() instanceof ServerWorld sw) {
                sw.spawnParticles(ParticleTypes.SNOWFLAKE,
                        player.getX(), player.getBodyY(0.6), player.getZ(),
                        10, 0.25, 0.25, 0.25, 0.01);

                sw.spawnParticles(ParticleTypes.SCULK_SOUL,
                        player.getX(), player.getBodyY(0.6), player.getZ(),
                        6, 0.25, 0.25, 0.25, 0.01);
            }
        }

        return ok;
    }

    // ---- GeckoLib ----
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // You can add attack/idle controllers later once you have animations.
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void createRenderer(Consumer<Object> consumer) {

    }

    @Override
    public Supplier<Object> getRenderProvider() {
        return null;
    }
}
