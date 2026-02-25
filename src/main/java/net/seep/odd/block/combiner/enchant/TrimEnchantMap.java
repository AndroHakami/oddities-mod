// src/main/java/net/seep/odd/block/combiner/enchant/TrimEnchantMap.java
package net.seep.odd.block.combiner.enchant;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

public final class TrimEnchantMap {
    private TrimEnchantMap() {}

    private static final Map<Identifier, Enchantment> MAP = new HashMap<>();

    /** Call after CombinerEnchantments.init() */
    public static void init() {
        // 1.20.1 has 16 trim templates. If you're using a backport for Flow/Bolt, these will work too.
        put("minecraft:sentry_armor_trim_smithing_template",     CombinerEnchantments.SENTRY);
        put("minecraft:dune_armor_trim_smithing_template",       CombinerEnchantments.DUNE);
        put("minecraft:coast_armor_trim_smithing_template",      CombinerEnchantments.COAST);
        put("minecraft:wild_armor_trim_smithing_template",       CombinerEnchantments.WILD);
        put("minecraft:ward_armor_trim_smithing_template",       CombinerEnchantments.WARD);
        put("minecraft:eye_armor_trim_smithing_template",        CombinerEnchantments.EYE);
        put("minecraft:vex_armor_trim_smithing_template",        CombinerEnchantments.VEX);
        put("minecraft:tide_armor_trim_smithing_template",       CombinerEnchantments.TIDE);
        put("minecraft:snout_armor_trim_smithing_template",      CombinerEnchantments.SNOUT);
        put("minecraft:rib_armor_trim_smithing_template",        CombinerEnchantments.RIB);
        put("minecraft:spire_armor_trim_smithing_template",      CombinerEnchantments.SPIRE);
        put("minecraft:wayfinder_armor_trim_smithing_template",  CombinerEnchantments.WAYFINDER);
        put("minecraft:shaper_armor_trim_smithing_template",     CombinerEnchantments.SHAPER);
        put("minecraft:silence_armor_trim_smithing_template",    CombinerEnchantments.SILENCE);
        put("minecraft:raiser_armor_trim_smithing_template",     CombinerEnchantments.RAISER);
        put("minecraft:host_armor_trim_smithing_template",       CombinerEnchantments.HOST);

        // Optional/backport ids:
        put("minecraft:flow_armor_trim_smithing_template",       CombinerEnchantments.FLOW);
        put("minecraft:bolt_armor_trim_smithing_template",       CombinerEnchantments.BOLT);
    }

    private static void put(String itemId, Enchantment ench) {
        MAP.put(Identifier.tryParse(itemId), ench);
    }

    public static Enchantment enchantFor(Item item) {
        Identifier id = Registries.ITEM.getId(item);
        return MAP.get(id);
    }
}