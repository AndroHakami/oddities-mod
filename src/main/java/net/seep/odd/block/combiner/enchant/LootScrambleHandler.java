// src/main/java/net/seep/odd/block/combiner/enchant/LootScrambleHandler.java
package net.seep.odd.block.combiner.enchant;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class LootScrambleHandler {
    private LootScrambleHandler() {}

    private static boolean installed = false;

    // ======= CONFIG =======
    /** 0.5% per valuable STACK (not per item-count). */
    private static final double SPEED_PER_VALUABLE = 0.005;
    /** hard cap so it can’t go insane */
    private static final double MAX_SPEED_BONUS = 0.25; // 25%

    /** refresh often enough but not every tick */
    private static final int CHECK_EVERY_TICKS = 10;

    private static final UUID MOD_UUID = UUID.fromString("b3d2a4df-0fe1-4f86-9f9f-03c6cf7e2a11");

    /** easy list: add/remove items here */
    private static final Set<Item> VALUABLES = Set.of(
            Items.DIAMOND, Items.DIAMOND_BLOCK,
            Items.EMERALD, Items.EMERALD_BLOCK,
            Items.NETHERITE_INGOT, Items.NETHERITE_BLOCK,
            Items.ANCIENT_DEBRIS,
            Items.GOLD_INGOT, Items.GOLD_BLOCK
    );

    private static final Map<UUID, Integer> tick = new HashMap<>();

    public static void init() {
        if (installed) return;
        installed = true;

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                tickPlayer(p);
            }
        });
    }

    private static void tickPlayer(ServerPlayerEntity p) {
        int t = tick.getOrDefault(p.getUuid(), 0) + 1;
        if (t < CHECK_EVERY_TICKS) {
            tick.put(p.getUuid(), t);
            return;
        }
        tick.put(p.getUuid(), 0);

        ItemStack legs = p.getEquippedStack(EquipmentSlot.LEGS);
        int lvl = (CombinerEnchantments.SNOUT == null) ? 0 : EnchantmentHelper.getLevel(CombinerEnchantments.SNOUT, legs);

        if (lvl <= 0) {
            removeSpeedMod(p);
            return;
        }

        int valuableStacks = countValuableStacks(p);

        // Resistance I if they have at least 1 valuable stack
        if (valuableStacks > 0) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 26, 0, true, false, false));
        }

        double bonus = Math.min(MAX_SPEED_BONUS, valuableStacks * SPEED_PER_VALUABLE);
        applySpeedMod(p, bonus);
    }

    private static int countValuableStacks(ServerPlayerEntity p) {
        int c = 0;
        for (ItemStack s : p.getInventory().main) {
            if (s.isEmpty()) continue;
            if (VALUABLES.contains(s.getItem())) c++;
        }
        return c;
    }

    private static void applySpeedMod(ServerPlayerEntity p, double bonus) {
        var inst = p.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (inst == null) return;

        inst.removeModifier(MOD_UUID);
        if (bonus <= 0.00001) return;

        inst.addPersistentModifier(new EntityAttributeModifier(
                MOD_UUID,
                "odd_loot_scramble_speed",
                bonus,
                EntityAttributeModifier.Operation.MULTIPLY_TOTAL
        ));
    }

    private static void removeSpeedMod(ServerPlayerEntity p) {
        var inst = p.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (inst == null) return;
        inst.removeModifier(MOD_UUID);
    }
}