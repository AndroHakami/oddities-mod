// src/main/java/net/seep/odd/block/combiner/enchant/LeechPlateHandler.java
package net.seep.odd.block.combiner.enchant;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class LeechPlateHandler {
    private LeechPlateHandler() {}

    private static boolean installed = false;

    // tuneables
    private static final float HEAL_PER_HIT = 1.0f;     // 1.0 = half-heart
    private static final float MAX_HEAL_PER_SEC = 4.0f; // 4.0 = 2 hearts / sec cap
    private static final int   WINDOW_TICKS = 20;

    private static final class Budget {
        long windowStart;
        float used;
    }

    private static final Map<UUID, Budget> budgets = new HashMap<>();

    public static void init() {
        if (installed) return;
        installed = true;

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            if (!(entity instanceof LivingEntity target) || !target.isAlive()) return ActionResult.PASS;

            if (CombinerEnchantments.RIB == null) return ActionResult.PASS;

            var chest = sp.getEquippedStack(EquipmentSlot.CHEST);
            if (chest == null || chest.isEmpty()) return ActionResult.PASS;

            if (EnchantmentHelper.getLevel(CombinerEnchantments.RIB, chest) <= 0) return ActionResult.PASS;

            // prevent silly heals on teammates (optional safety)
            if (target.isTeammate(sp)) return ActionResult.PASS;

            long now = sp.getWorld().getTime();
            Budget b = budgets.computeIfAbsent(sp.getUuid(), k -> new Budget());

            if (now - b.windowStart >= WINDOW_TICKS) {
                b.windowStart = now;
                b.used = 0f;
            }

            float remaining = MAX_HEAL_PER_SEC - b.used;
            if (remaining <= 0.001f) return ActionResult.PASS;

            float heal = Math.min(HEAL_PER_HIT, remaining);

            // only heal if not already full
            if (sp.getHealth() < sp.getMaxHealth()) {
                sp.heal(heal);
                b.used += heal;
            }

            return ActionResult.PASS;
        });
    }
}