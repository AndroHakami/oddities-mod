// FILE: src/main/java/net/seep/odd/item/wizard/WalkingStickItem.java
package net.seep.odd.item.wizard;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

import net.seep.odd.abilities.PowerAPI;
import net.seep.odd.abilities.power.Powers;
import net.seep.odd.abilities.power.WizardPower;
import net.seep.odd.abilities.wizard.WizardCasting;
import net.seep.odd.abilities.wizard.WizardElement;

import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class WalkingStickItem extends Item implements GeoItem {
    /** Stored on the stack so *everyone* renders the correct element texture. */
    public static final String NBT_ELEMENT_KEY = "OddWizardElement";

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation CHARGING = RawAnimation.begin().thenLoop("charging");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /**
     * GeckoLib's dynamic item renderer provider (Fabric).
     * NOTE: We keep this as Supplier<Object> (per GeckoLib docs).
     */
    private final Supplier<Object> renderProvider = GeoItem.makeRenderer(this);

    public WalkingStickItem(Settings settings) {
        super(settings.maxCount(1));
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    // ---------------------------
    // Vanilla item behaviour
    // ---------------------------

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.BOW; // puts player in "using" state while holding RMB
    }

    @Override
    public int getMaxUseTime(ItemStack stack) {
        return 72000;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        user.setCurrentHand(hand);
        return TypedActionResult.consume(stack);
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (world.isClient) return;
        if (!(user instanceof ServerPlayerEntity p)) return;

        // only if player is wizard
        if (!(Powers.get(PowerAPI.get(p)) instanceof WizardPower)) return;

        WizardElement e = WizardPower.getElement(p);
        int used = this.getMaxUseTime(stack) - remainingUseTicks;
        int needed = WizardCasting.chargeTicksFor(e);

        if (used < needed) return; // not fully charged -> do nothing

        WizardCasting.castBig(p); // fully charged -> big cast
    }

    /**
     * Server-side: keep the stack's NBT updated with the player's current element,
     * so textures swap the moment element changes.
     */
    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (world.isClient) return;
        if (!(entity instanceof ServerPlayerEntity p)) return;

        if (!(Powers.get(PowerAPI.get(p)) instanceof WizardPower)) return;

        WizardElement e = WizardPower.getElement(p);
        String elementId = (e == null) ? "none" : e.name().toLowerCase(Locale.ROOT);

        NbtCompound nbt = stack.getOrCreateNbt();
        if (!elementId.equals(nbt.getString(NBT_ELEMENT_KEY))) {
            nbt.putString(NBT_ELEMENT_KEY, elementId);
        }
    }

    // ---------------------------
    // GeckoLib (animations)
    // ---------------------------

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, this::predicate));
    }

    private PlayState predicate(AnimationState<WalkingStickItem> state) {
        // When rendered in-hand, GeckoLib supplies the holder entity (player/mob) here on GeoItems.
        Entity holder = state.getData(DataTickets.ENTITY);

        boolean charging = false;
        if (holder instanceof LivingEntity living) {
            // "charging" if this living entity is currently using a WalkingStickItem
            charging = living.isUsingItem() && living.getActiveItem().getItem() instanceof WalkingStickItem;
        }

        state.getController().setAnimation(charging ? CHARGING : IDLE);
        return PlayState.CONTINUE;
    }

    // ---------------------------
    // GeckoLib (renderer hookup - Fabric)
    // SAFE: no net.minecraft.client refs in this class; we instantiate via reflection.
    // ---------------------------

    @Override
    public Supplier<Object> getRenderProvider() {
        return this.renderProvider;
    }

    @Override
    public void createRenderer(Consumer<Object> consumer) {
        try {
            // Client-only class (keeps dedicated server safe)
            Class<?> clazz = Class.forName("net.seep.odd.item.wizard.client.WalkingStickRenderProvider");
            Object provider = clazz.getDeclaredConstructor().newInstance();
            consumer.accept(provider);
        } catch (Throwable ignored) {
            // If something goes wrong, you'll see missing renderer symptoms (purple/black).
        }
    }
}
