// src/main/java/net/seep/odd/block/combiner/enchant/DriftwoodGuardHandler.java
package net.seep.odd.block.combiner.enchant;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ShieldItem;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

public final class DriftwoodGuardHandler {
    private DriftwoodGuardHandler() {}

    private static final UUID NO_SLOW_UUID = UUID.fromString("f3b0b2e2-2d28-4f3d-9b8e-6e9a6c2b8a11");
    // Vanilla "using item" slow is ~0.2 => we multiply speed by 5 to cancel it (1 + 4 = 5)
    private static final EntityAttributeModifier NO_SLOW_MOD =
            new EntityAttributeModifier(NO_SLOW_UUID, "odd:driftwood_guard_no_slow", 4.0, EntityAttributeModifier.Operation.MULTIPLY_TOTAL);

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                tick(p);
            }
        });
    }

    private static void tick(ServerPlayerEntity p) {
        EntityAttributeInstance inst = p.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (inst == null) return;

        boolean shouldHave = isBlockingWithDriftwoodGuard(p);

        if (shouldHave) {
            if (inst.getModifier(NO_SLOW_UUID) == null) {
                inst.addTemporaryModifier(NO_SLOW_MOD);
            }
        } else {
            if (inst.getModifier(NO_SLOW_UUID) != null) {
                inst.removeModifier(NO_SLOW_UUID);
            }
        }
    }

    private static boolean isBlockingWithDriftwoodGuard(ServerPlayerEntity p) {
        if (p.hasVehicle()) return false;
        if (!p.isUsingItem()) return false;

        ItemStack active = p.getActiveItem();
        if (active.isEmpty() || !(active.getItem() instanceof ShieldItem)) return false;

        return EnchantmentHelper.getLevel(CombinerEnchantments.COAST, active) > 0;
    }
}