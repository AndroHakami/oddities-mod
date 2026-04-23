// net/seep/odd/item/custom/CosmicKatanaItem.java
package net.seep.odd.item.custom;

import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

import net.seep.odd.abilities.PowerAPI;
import net.seep.odd.status.ModStatusEffects;

import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;
import software.bernie.geckolib.util.RenderUtils;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class CosmicKatanaItem extends SwordItem implements GeoItem {

    private static final String REQUIRED_POWER_ID = "cosmic";

    private static final int BLOCK_MAX_USE_TICKS = 72000;
    private static final float BLOCK_DAMAGE_REDUCTION = 0.4f;

    private static final RawAnimation IDLE  = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation BLOCK = RawAnimation.begin().thenLoop("block");

    private static boolean hooksInstalled = false;

    // ✅ re-apply reduced damage safely without infinite recursion
    private static final ThreadLocal<Boolean> ODD_KATANA_GUARD = ThreadLocal.withInitial(() -> false);

    public CosmicKatanaItem(Settings settings) {
        super(ToolMaterials.NETHERITE, 3, -2.2f, settings);
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
        installHooksOnce();
    }

    private static boolean canUse(PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity sp)) return true; // client: let server decide
        if (sp.hasStatusEffect(ModStatusEffects.POWERLESS)) return false;
        String id = PowerAPI.get(sp);
        return REQUIRED_POWER_ID.equals(id);
    }

    private static boolean isActivelyBlockingWithKatana(PlayerEntity p) {
        if (!p.isUsingItem()) return false;
        ItemStack active = p.getActiveItem();
        return !active.isEmpty() && active.getItem() instanceof CosmicKatanaItem;
    }

    private static TypedActionResult<ItemStack> deny(World world, PlayerEntity user, ItemStack stack) {
        if (!world.isClient) {
            user.sendMessage(Text.literal("Only a Cosmic can use this."), true);
            if (user instanceof ServerPlayerEntity sp) {
                sp.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 0.6f, 0.6f);
            }
            // cancels THIS attempted katana block, not random stuff
            user.stopUsingItem();
        }
        return TypedActionResult.fail(stack);
    }

    private static void installHooksOnce() {
        if (hooksInstalled) return;
        hooksInstalled = true;

        // Prevent non-cosmic from attacking with the katana
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            ItemStack main = sp.getMainHandStack();
            if (main.getItem() instanceof CosmicKatanaItem && !canUse(sp)) return ActionResult.FAIL;
            return ActionResult.PASS;
        });

        AttackBlockCallback.EVENT.register((player, world, hand, pos, dir) -> {
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            ItemStack main = sp.getMainHandStack();
            if (main.getItem() instanceof CosmicKatanaItem && !canUse(sp)) return ActionResult.FAIL;
            return ActionResult.PASS;
        });

        /**
         * ✅ Damage reduction for 1.20.1 Fabric:
         * ServerLivingEntityEvents has no MODIFY_DAMAGE in your version, so we:
         * - CANCEL original damage (return false)
         * - re-apply reduced damage ourselves with a ThreadLocal guard
         *
         * ✅ IMPORTANT: This only triggers if the player is actively blocking with the Cosmic Katana.
         * It WILL NOT cancel eating/shields/etc anymore.
         */
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (ODD_KATANA_GUARD.get()) return true; // allow internal re-apply call
            if (!(entity instanceof PlayerEntity player)) return true;

            // only relevant if actively blocking with THIS katana
            if (!isActivelyBlockingWithKatana(player)) return true;

            // if they became not allowed while blocking with katana, stop blocking (but don't touch other item uses)
            if (!canUse(player)) {
                player.stopUsingItem(); // ✅ safe: only stops katana block
                return true;
            }

            // cooldown + sound (cosmetic)
            ItemStack active = player.getActiveItem();
            if (!player.getItemCooldownManager().isCoolingDown(active.getItem())) {
                player.getItemCooldownManager().set(active.getItem(), 8);
            }

            entity.getWorld().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 0.25f, 1.35f);

            float reduced = amount * (1f - BLOCK_DAMAGE_REDUCTION);

            // fully blocked
            if (reduced <= 0.0f) return false;

            // cancel original, deal reduced ourselves
            try {
                ODD_KATANA_GUARD.set(true);
                entity.damage(source, reduced);
            } finally {
                ODD_KATANA_GUARD.set(false);
            }

            return false; // cancel original damage
        });
    }

    /* =========================
       Vanilla use (block)
       ========================= */

    @Override public UseAction getUseAction(ItemStack stack) { return UseAction.BLOCK; }
    @Override public int getMaxUseTime(ItemStack stack) { return BLOCK_MAX_USE_TICKS; }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        // server gate
        if (!world.isClient && !canUse(user)) return deny(world, user, stack);

        user.setCurrentHand(hand);
        user.incrementStat(Stats.USED.getOrCreateStat(this));

        if (!world.isClient) {
            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 0.6f, 1.25f);
        }

        return TypedActionResult.consume(stack);
    }

    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        if (world.isClient) return;

        // ✅ only stop using if they are using THIS katana but are not allowed
        if (user instanceof PlayerEntity p) {
            if (isActivelyBlockingWithKatana(p) && !canUse(p)) {
                p.stopUsingItem();
            }
        }
    }

    /* =========================
       GeckoLib plumbing
       ========================= */

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private final Supplier<Object> renderProvider = GeoItemClientHooks.createRenderProvider(this);

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", state -> {
            PlayerEntity p;
            Object e = state.getData(DataTickets.ENTITY);
            if (e instanceof PlayerEntity pe) p = pe;
            else p = getClientPlayer();

            boolean blocking = false;
            if (p != null) {
                blocking = p.isUsingItem()
                        && p.getActiveItem() != null
                        && p.getActiveItem().isOf(this);
            }

            state.setAndContinue(blocking ? BLOCK : IDLE);
            return PlayState.CONTINUE;
        }));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
    @Override public Supplier<Object> getRenderProvider() { return renderProvider; }
    @Override public double getTick(Object animatable) { return RenderUtils.getCurrentTick(); }

    @Override
    public void createRenderer(Consumer<Object> consumer) {
        GeoItemClientHooks.createGeoItemRenderer(consumer,
                "net.seep.odd.item.custom.client.CosmicKatanaItemRenderer");
    }

    private static PlayerEntity getClientPlayer() {
        return GeoItemClientHooks.getClientPlayerOrNull();
    }
}