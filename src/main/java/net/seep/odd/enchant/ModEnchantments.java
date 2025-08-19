package net.seep.odd.enchant;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;

public final class ModEnchantments {
    private ModEnchantments(){}
    public static final Enchantment LUCKY_DAY = Registry.register(
            Registries.ENCHANTMENT,
            new Identifier(Oddities.MOD_ID, "lucky_day"),
            new LuckyDayEnchantment()
    );

    /** Reapply/remove Luck every tick so it persists after milk and respects day/night immediately. */
    public static void registerTicker() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                if (p.isSpectator() || p.isCreative()) continue;

                int lvl = EnchantmentHelper.getLevel(LUCKY_DAY, p.getEquippedStack(EquipmentSlot.LEGS));
                boolean isDay = p.getWorld().isDay() && p.getWorld().getDimension().hasSkyLight();

                if (lvl > 0 && isDay) {
                    // refresh a short ambient effect every tick (persists even after milk)
                    p.addStatusEffect(new StatusEffectInstance(
                            StatusEffects.LUCK, 40, 0, true, false, true
                    ));
                } else {
                    // turn off at night quickly (no flicker)
                    if (p.hasStatusEffect(StatusEffects.LUCK)) {
                        p.removeStatusEffect(StatusEffects.LUCK);
                    }
                }
            }
        });
    }

    public static Enchantment ITALIAN_STOMPERS;

    public static void register() {
        ITALIAN_STOMPERS = Registry.register(
                Registries.ENCHANTMENT,
                new Identifier(Oddities.MOD_ID, "italian_stompers"),
                new ItalianStompersEnchantment()
        );
        Oddities.LOGGER.info("Registered enchantments for {}", Oddities.MOD_ID);
    }
}
