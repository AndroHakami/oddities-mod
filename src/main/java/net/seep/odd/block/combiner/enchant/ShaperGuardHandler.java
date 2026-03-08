// src/main/java/net/seep/odd/block/combiner/enchant/ShaperGuardHandler.java
package net.seep.odd.block.combiner.enchant;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;

public final class ShaperGuardHandler {
    private ShaperGuardHandler() {}

    private static boolean installed = false;
    private static final int DURATION = 10; // 0.5s
    private static final int AMP = 2;       // Resistance III

    public static void init() {
        if (installed) return;
        installed = true;

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            if (!(entity instanceof LivingEntity le) || !le.isAlive()) return ActionResult.PASS;

            ItemStack w = sp.getMainHandStack();
            if (!(w.getItem() instanceof SwordItem)) return ActionResult.PASS;

            if (CombinerEnchantments.SHAPER == null) return ActionResult.PASS;
            if (EnchantmentHelper.getLevel(CombinerEnchantments.SHAPER, w) <= 0) return ActionResult.PASS;

            sp.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, DURATION, AMP, true, false, false));
            return ActionResult.PASS;
        });
    }
}