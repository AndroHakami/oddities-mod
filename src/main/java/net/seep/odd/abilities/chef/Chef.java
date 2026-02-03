package net.seep.odd.abilities.chef;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import net.seep.odd.Oddities;

import java.lang.reflect.Method;

public final class Chef {
    private Chef() {}

    public static final String POWER_ID = "chef";
    public static final float DOUBLE_LOOT_CHANCE = 0.25f;

    public static final TagKey<Item> INGREDIENTS_TAG =
            TagKey.of(RegistryKeys.ITEM, new Identifier(Oddities.MOD_ID, "chef_ingredients"));

    public static boolean isIngredient(ItemStack stack) {
        return !stack.isEmpty() && stack.isIn(INGREDIENTS_TAG);
    }

    /** Replace this with your real power system check when you want. */
    public static boolean hasChefPower(Entity e) {
        if (!(e instanceof PlayerEntity p)) return false;

        // Fast path: scoreboard tags (easy to test)
        if (p.getScoreboardTags().contains("chef")) return true;
        if (p.getScoreboardTags().contains("odd:chef")) return true;
        if (p.getScoreboardTags().contains("odd_power:chef")) return true;

        // Optional reflection hooks (won't crash if missing)
        try {
            Class<?> cls = Class.forName("net.seep.odd.abilities.power.PowerManager");
            Method m = cls.getMethod("hasPower", PlayerEntity.class, String.class);
            Object res = m.invoke(null, p, POWER_ID);
            if (res instanceof Boolean b && b) return true;
        } catch (Throwable ignored) {}

        try {
            Class<?> cls = Class.forName("net.seep.odd.abilities.power.Powers");
            Method m = cls.getMethod("getId", PlayerEntity.class);
            Object res = m.invoke(null, p);
            if (res instanceof String s && POWER_ID.equals(s)) return true;
        } catch (Throwable ignored) {}

        return false;
    }

    /** Server-side roll for doubling loot. */
    public static boolean rollDoubleLoot(LivingEntity victim, Entity attacker, Random random) {
        if (!(victim instanceof AnimalEntity)) return false;
        if (!(attacker instanceof ServerPlayerEntity)) return false;
        if (!hasChefPower(attacker)) return false;
        return random.nextFloat() < DOUBLE_LOOT_CHANCE;
    }

    /** ThreadLocal context used by mixins to duplicate drops only during dropLoot(). */
    public static final class LootTL {
        private LootTL() {}
        public static final ThreadLocal<LivingEntity> CURRENT = new ThreadLocal<>();
        public static final ThreadLocal<Boolean> DOUBLE = ThreadLocal.withInitial(() -> false);
        public static final ThreadLocal<Boolean> GUARD  = ThreadLocal.withInitial(() -> false);

        public static void begin(LivingEntity victim, boolean dbl) {
            CURRENT.set(victim);
            DOUBLE.set(dbl);
        }

        public static void end() {
            CURRENT.remove();
            DOUBLE.remove();
            GUARD.remove();
        }
    }
}
