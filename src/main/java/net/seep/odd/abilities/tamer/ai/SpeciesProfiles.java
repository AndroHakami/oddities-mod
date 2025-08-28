package net.seep.odd.abilities.tamer.ai;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.seep.odd.abilities.tamer.ai.behavior.*;
import net.seep.odd.entity.ModEntities;

import java.util.*;

public final class SpeciesProfiles {
    private SpeciesProfiles() {}

    /** entityTypeId -> list of behaviors to add (combat-specific) */
    private static final Map<Identifier, List<CompanionBehavior>> MAP = new HashMap<>();

    /** Call once in your mod init (after your content reg is done). */
    public static void init() {
        // Villager: ranged shuriken kiting
        register(EntityType.VILLAGER,
                new RangeShurikenBehavior(1.25, 4.0, 9.0, 18, 3.5));
        register(net.minecraft.entity.EntityType.SHEEP,
                new ChargeTackleBehavior(20, 1.35, 6.0f, 1.2, 150));
        register(net.minecraft.entity.EntityType.SHEEP,
                new MeleeFixedDamageBehavior(10,10, 10));
        register(net.minecraft.entity.EntityType.COW,
                new ChargeTackleBehavior(20, 1.25, 7.0f, 1.4, 70));
        register(EntityType.GOAT,
                new ChargeTackleBehavior(20, 3.25, 4.0f, 4, 30));
        register(ModEntities.VILLAGER_EVO, new HeadButterBehavior());

        register(net.minecraft.entity.EntityType.CHICKEN,
                new ChargeTackleBehavior(20, 1.55, 4.0f, 0.9, 50));
    }

        // You can add more:
        // register(EntityType.SPIDER, new MeleeVanillaBehavior(1.2, true));
        // register(ModEntities.VILLAGER_EVO, new MeleeVanillaBehavior(1.05, true));


    public static void register(EntityType<?> type, CompanionBehavior... behaviors) {
        Identifier id = Registries.ENTITY_TYPE.getId(type);
        MAP.computeIfAbsent(id, k -> new ArrayList<>()).addAll(Arrays.asList(behaviors));
    }

    /** Apply species profile; if none present, choose a sensible melee default. */
    public static void applySpeciesCombat(MobEntity mob, ServerPlayerEntity owner, GoalSelector goals, GoalSelector targets) {
        Identifier id = Registries.ENTITY_TYPE.getId(mob.getType());
        List<CompanionBehavior> list = MAP.get(id);
        if (list != null && !list.isEmpty()) {
            for (var b : list) b.apply(mob, owner, goals, targets);
            return;
        }

        // Fallback: vanilla melee if ATTACK_DAMAGE exists, else fixed-dmg melee
        var atk = mob.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (atk != null) {
            new MeleeVanillaBehavior(1.25, true).apply(mob, owner, goals, targets);
        } else {
            new MeleeFixedDamageBehavior(1.20, 3.0f, 18).apply(mob, owner, goals, targets);
        }
    }
}
