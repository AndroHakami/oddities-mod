package net.seep.odd.abilities.rat.food;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Set;

/** Group vanilla foods + apply small themed buffs to the host (not the rat). */
public final class RatFoodLogic {
    private RatFoodLogic() {}

    /* ========= Groups (1.20.x vanilla edible items) ========= */

    public static final Set<Item> FRUITS_VEG = Set.of(
            Items.APPLE, Items.CHORUS_FRUIT,
            Items.CARROT, Items.GOLDEN_CARROT,
            Items.POTATO, Items.BAKED_POTATO, Items.POISONOUS_POTATO,
            Items.BEETROOT,
            Items.SWEET_BERRIES, Items.GLOW_BERRIES,
            Items.MELON_SLICE
    );

    public static final Set<Item> BAKED_GOODS = Set.of(
            Items.BREAD, Items.COOKIE, Items.PUMPKIN_PIE
            // (Cake is a block to bite; handled outside item consumption)
    );

    public static final Set<Item> MEAT_COOKED = Set.of(
            Items.COOKED_BEEF, Items.COOKED_PORKCHOP, Items.COOKED_MUTTON,
            Items.COOKED_CHICKEN, Items.COOKED_RABBIT
    );

    public static final Set<Item> MEAT_RAW = Set.of(
            Items.BEEF, Items.PORKCHOP, Items.MUTTON, Items.CHICKEN, Items.RABBIT
    );

    public static final Set<Item> FISH_COOKED = Set.of(
            Items.COOKED_COD, Items.COOKED_SALMON
    );

    public static final Set<Item> FISH_RAW = Set.of(
            Items.COD, Items.SALMON, Items.TROPICAL_FISH, Items.PUFFERFISH
    );

    public static final Set<Item> STEWS = Set.of(
            Items.MUSHROOM_STEW, Items.RABBIT_STEW, Items.BEETROOT_SOUP, Items.SUSPICIOUS_STEW
    );

    public static final Set<Item> SWEETS_N_SUGARS = Set.of(
            Items.HONEY_BOTTLE
    );

    public static final Set<Item> DRIEDS_ODDS = Set.of(
            Items.DRIED_KELP, Items.ROTTEN_FLESH, Items.SPIDER_EYE
    );

    public static final Set<Item> GILDED = Set.of(
            Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE, Items.GOLDEN_CARROT
    );

    /* ========= Logic ========= */

    /** Slight extra saturation for the rat itself (stack-safe, modest). */
    public static void giveRatBonusSaturation(ServerPlayerEntity rat, int food, float sat) {
        // +50% of the eaten saturation component (very mild in practice)
        float bonus = Math.max(0f, sat) * food * 0.50f;
        rat.getHungerManager().add(0, bonus);
    }

    /** Share some food to the host and apply a small themed buff (not to the rat). */
    public static void shareWithHostAndBuff(ServerPlayerEntity rat, ServerPlayerEntity host, Item item) {
        // Feed about 40% of what rat ate; cap is handled by HungerManager
        int shareFood = Math.round(4 * 0.40f);   // typical foods are 2-6; this is a safe average
        float shareSat = 0.6f;                   // small nudge of saturation
        host.getHungerManager().add(shareFood, shareSat);

        // Pick tiny buff by group (all effects short & level 0/1)
        StatusEffectInstance fx = pickBuffFor(item);

        if (fx != null) {
            host.addStatusEffect(fx);
        }
    }

    private static StatusEffectInstance pickBuffFor(Item item) {
        int t = 20; // seconds -> multiply by 20 later
        if (FRUITS_VEG.contains(item))        return new StatusEffectInstance(StatusEffects.REGENERATION, 6 * t, 0, true, true, true);
        if (BAKED_GOODS.contains(item))       return new StatusEffectInstance(StatusEffects.HASTE,        10 * t, 0, true, true, true);
        if (MEAT_COOKED.contains(item))       return new StatusEffectInstance(StatusEffects.STRENGTH,     8 * t, 0, true, true, true);
        if (MEAT_RAW.contains(item))          return new StatusEffectInstance(StatusEffects.RESISTANCE,   5 * t, 0, true, true, true); // still "nice"
        if (FISH_COOKED.contains(item))       return new StatusEffectInstance(StatusEffects.DOLPHINS_GRACE, 6 * t, 0, true, true, true);
        if (FISH_RAW.contains(item))          return new StatusEffectInstance(StatusEffects.WATER_BREATHING, 8 * t, 0, true, true, true);
        if (STEWS.contains(item))             return new StatusEffectInstance(StatusEffects.ABSORPTION,   10 * t, 0, true, true, true);
        if (SWEETS_N_SUGARS.contains(item))   return new StatusEffectInstance(StatusEffects.SPEED,        10 * t, 0, true, true, true);
        if (DRIEDS_ODDS.contains(item))       return new StatusEffectInstance(StatusEffects.SATURATION,   1 * t, 0, true, true, true);
        if (GILDED.contains(item))            return new StatusEffectInstance(StatusEffects.RESISTANCE,   10 * t, 1, true, true, true);

        // default mild perk for anything edible we missed
        return new StatusEffectInstance(StatusEffects.LUCK, 8 * t, 0, true, true, true);
    }
}
