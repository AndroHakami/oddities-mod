package net.seep.odd.mixin;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.world.World;
import net.seep.odd.abilities.conquer.CorruptionCureHolder;
import net.seep.odd.abilities.power.ConquerPower;

import net.seep.odd.status.ModStatusEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VillagerEntity.class)
public abstract class VillagerEntityMixin implements CorruptionCureHolder {

    @Unique private static final String ODD_CURE_TICKS_KEY = "odd_corruption_cure_ticks";

    @Unique private int odd$cureTicks = 0;

    @Override public int odd$getCorruptionCureTicks() { return odd$cureTicks; }
    @Override public void odd$setCorruptionCureTicks(int ticks) { odd$cureTicks = ticks; }

    @Unique private boolean odd$discountApplied = false;
    @Unique private final IntArrayList odd$discounts = new IntArrayList();

    @Inject(method = "interactMob", at = @At("HEAD"), cancellable = true)
    private void odd$interactMob(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        VillagerEntity self = (VillagerEntity)(Object)this;
        World world = self.getWorld();

        if (!self.hasStatusEffect(ModStatusEffects.CORRUPTION)) return;

        // If currently curing, ignore interactions
        if (odd$cureTicks > 0) {
            cir.setReturnValue(ActionResult.SUCCESS);
            return;
        }

        ItemStack held = player.getStackInHand(hand);

        // Start cure: Weakness + Golden Apple
        if (!world.isClient
                && held.isOf(Items.GOLDEN_APPLE)
                && self.hasStatusEffect(StatusEffects.WEAKNESS)) {

            odd$cureTicks = 20 * 20; // 20s
            if (!player.getAbilities().creativeMode) held.decrement(1);

            world.playSound(null, self.getBlockPos(),
                    SoundEvents.ENTITY_ZOMBIE_VILLAGER_CURE,
                    SoundCategory.NEUTRAL, 0.9f, 1.1f);

            cir.setReturnValue(ActionResult.SUCCESS);
            return;
        }

        // Trade lock: ONLY conquer can trade with corrupted villagers
        if (!world.isClient) {
            if (!(player instanceof ServerPlayerEntity sp) || !ConquerPower.hasConquer(sp)) {
                player.sendMessage(net.minecraft.text.Text.literal("This villager will only trade with Conquer."), true);
                cir.setReturnValue(ActionResult.SUCCESS);
            }
        }
    }

    /** Apply discount when trading starts; revert when trading ends. */
    @Inject(method = "setCustomer", at = @At("HEAD"))
    private void odd$setCustomerPre(PlayerEntity customer, CallbackInfo ci) {
        VillagerEntity self = (VillagerEntity)(Object)this;

        // Trade closing: revert discount
        if (customer == null && odd$discountApplied) {
            TradeOfferList offers = self.getOffers();
            int n = Math.min(offers.size(), odd$discounts.size());
            for (int i = 0; i < n; i++) {
                TradeOffer offer = offers.get(i);
                int amt = odd$discounts.getInt(i);
                offer.increaseSpecialPrice(amt);
            }
            odd$discounts.clear();
            odd$discountApplied = false;
            return;
        }

        // Trade opening: apply discount if corrupted + conquer
        if (customer != null
                && self.hasStatusEffect(ModStatusEffects.CORRUPTION)
                && odd$cureTicks == 0
                && (customer instanceof ServerPlayerEntity sp)
                && ConquerPower.hasConquer(sp)
                && !odd$discountApplied) {

            TradeOfferList offers = self.getOffers();
            if (offers == null || offers.isEmpty()) return;

            odd$discounts.clear();

            for (TradeOffer offer : offers) {
                int baseCost = offer.getAdjustedFirstBuyItem().getCount();
                int discount = Math.max(1, Math.min(12, baseCost / 3));
                offer.increaseSpecialPrice(-discount);
                odd$discounts.add(discount);
            }

            odd$discountApplied = true;
        }
    }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void odd$writeNbt(NbtCompound nbt, CallbackInfo ci) {
        if (odd$cureTicks > 0) nbt.putInt(ODD_CURE_TICKS_KEY, odd$cureTicks);
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void odd$readNbt(NbtCompound nbt, CallbackInfo ci) {
        odd$cureTicks = nbt.getInt(ODD_CURE_TICKS_KEY);
    }
}
