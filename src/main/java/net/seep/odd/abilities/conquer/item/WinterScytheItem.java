// src/main/java/net/seep/odd/abilities/conquer/item/WinterScytheItem.java
package net.seep.odd.abilities.conquer.item;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;
import net.seep.odd.abilities.power.ConquerPower;

import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.animatable.client.RenderProvider;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;
import java.util.function.Supplier;

public final class WinterScytheItem extends SwordItem implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Supplier<Object> renderProvider = GeoItem.makeRenderer(this);

    private static final String NBT_SPIN_BLOCKING = "odd_spin_blocking";

    // GeckoLib animations
    private static final RawAnimation ANIM_SPIN_BLOCK =
            RawAnimation.begin().thenLoop("spin_block");

    public WinterScytheItem(Settings settings) {
        super(ToolMaterials.DIAMOND, 4, -2.0f, settings);
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    // Acts like a shield when holding right click
    @Override public int getMaxUseTime(ItemStack stack) { return 72000; }
    @Override public UseAction getUseAction(ItemStack stack) { return UseAction.BLOCK; }

    // ---- Conquer-only lock ----
    private static boolean allowed(PlayerEntity player) {
        return ConquerPower.hasConquer(player);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (!allowed(user)) {
            if (!world.isClient) user.sendMessage(Text.literal("Only Conquer can wield the Winter Scythe."), true);
            return TypedActionResult.fail(stack);
        }

        // Start blocking (spin_block)
        user.setCurrentHand(hand);

        if (!world.isClient) {
            // Only play once on start
            var nbt = stack.getOrCreateNbt();
            if (!nbt.getBoolean(NBT_SPIN_BLOCKING)) {
                nbt.putBoolean(NBT_SPIN_BLOCKING, true);

                world.playSound(
                        null,
                        user.getX(), user.getY(), user.getZ(),
                        SoundEvents.ITEM_TRIDENT_RIPTIDE_1,
                        SoundCategory.PLAYERS,
                        0.55f,
                        1.25f
                );
            }
        }

        return TypedActionResult.consume(stack);
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remaining) {
        super.onStoppedUsing(stack, world, user, remaining);
        if (world.isClient) return;
        if (stack.hasNbt() && stack.getNbt() != null) {
            stack.getNbt().putBoolean(NBT_SPIN_BLOCKING, false);
        }
    }


    public void inventoryTick(ItemStack stack, World world, LivingEntity entity, int slot, boolean selected) {
        super.inventoryTick(stack, world, entity, slot, selected);

        if (world.isClient) return;
        if (!(entity instanceof PlayerEntity player)) return;

        // If not Conquer, rip it out and drop it
        if (!allowed(player)) {
            ItemStack copy = stack.copy();
            yankFromPlayer(player, stack);
            player.dropItem(copy, true);

            if (player.age % 40 == 0) {
                player.sendMessage(Text.literal("Only Conquer can wield the Winter Scythe."), true);
            }
            return;
        }

        // If player is NOT actively using this scythe, make sure we clear the blocking flag
        // (prevents getting “stuck” in spin_block if something interrupts)
        if (stack.hasNbt() && stack.getNbt() != null) {
            boolean blocking = stack.getNbt().getBoolean(NBT_SPIN_BLOCKING);
            if (blocking) {
                boolean actuallyUsingThis =
                        player.isUsingItem() && player.getActiveItem() == stack;
                if (!actuallyUsingThis) {
                    stack.getNbt().putBoolean(NBT_SPIN_BLOCKING, false);
                }
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

    /* ------- GeckoLib wiring ------- */

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, state -> {
            ItemStack stack = state.getData(DataTickets.ITEMSTACK);
            if (stack != null && stack.hasNbt() && stack.getNbt() != null && stack.getNbt().getBoolean(NBT_SPIN_BLOCKING)) {
                state.getController().setAnimation(ANIM_SPIN_BLOCK);
                return PlayState.CONTINUE;
            }
            return PlayState.STOP;
        }));
    }

    @Override
    public void createRenderer(Consumer<Object> consumer) {
        consumer.accept(new RenderProvider() {
            private GeoItemRenderer<?> renderer;

            @Override
            public GeoItemRenderer<?> getCustomRenderer() {
                if (renderer == null) {
                    renderer = new net.seep.odd.abilities.conquer.client.render.WinterScytheRenderer();
                }
                return renderer;
            }
        });
    }

    @Override public Supplier<Object> getRenderProvider() { return renderProvider; }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
