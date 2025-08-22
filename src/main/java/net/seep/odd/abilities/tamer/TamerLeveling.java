package net.seep.odd.abilities.tamer;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.seep.odd.entity.ModEntities;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Central leveling: kill → XP, thresholds → level up, level → attribute scaling, evo gate. */
public final class TamerLeveling {
    private TamerLeveling() {}

    // one-time event registration
    private static final AtomicBoolean INIT = new AtomicBoolean(false);
    public static void ensureInit() {
        if (!INIT.compareAndSet(false, true)) return;

        // Award XP when something dies, if the attacker is a currently-active pet
        ServerLivingEntityEvents.AFTER_DEATH.register((victim, source) -> {
            Entity attacker = source.getAttacker();
            if (!(victim.getWorld() instanceof ServerWorld sw)) return;
            if (attacker == null) return;

            UUID killerId = attacker.getUuid();

            TamerState st = TamerState.get(sw);
            UUID ownerId = st.findOwnerOfActiveEntity(killerId);
            if (ownerId == null) return;

            // Which slot is active?
            TamerState.Active active = st.getActive(ownerId);
            if (active == null) return;

            List<PartyMember> party = st.partyOf(ownerId);
            if (active.index < 0 || active.index >= party.size()) return;
            PartyMember pm = party.get(active.index);

            int gain = TamerXp.xpForKill(victim);
            int beforeLevel = pm.level;

            // Increment XP; PartyMember is expected to store xp + level
            pm.exp += gain;
            // Level up while XP exceeds threshold
            while (pm.level < PartyMember.MAX_LEVEL &&
                    pm.exp >= TamerXp.totalExpForLevel(pm.level + 1)) {
                pm.level++;
            }

            // Feedback + persist
            st.markDirty();
            ServerPlayerEntity owner = sw.getServer().getPlayerManager().getPlayer(ownerId);
            if (owner != null) {
                if (pm.level > beforeLevel) {
                    owner.sendMessage(Text.literal(pm.displayName() + " leveled up to " + pm.level + "!"), true);
                } else {
                    owner.sendMessage(Text.literal("+" + gain + " XP to " + pm.displayName()), true);
                }
            }

            // Apply live scaling and evolution if the active mob is still around
            Entity e = sw.getEntity(active.entity);
            if (e instanceof MobEntity mob && mob.isAlive()) {
                applyLevelTo(mob, pm.level);
                maybeEvolveActive(sw, owner, st, active.index, pm, mob);
            }
        });
    }

    /** Init baseline on capture if you want (optional; safe to call). */
    public static void onCaptured(MobEntity mob, ServerPlayerEntity owner) {
        // no-op for now; PartyMember created with base level/xp elsewhere
    }

    /** Scale health/attack a bit by level. Idempotent-ish (reapplies base+bonus). */
    public static void applyLevelTo(MobEntity mob, int level) {
        level = Math.max(1, Math.min(PartyMember.MAX_LEVEL, level));

        // Attack: base + 0.3 per level
        var atk = mob.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (atk != null) {
            double base = Math.max(2.0, atk.getBaseValue());
            atk.setBaseValue(base + 0.30 * (level - 1));
        }

        // Health: base + 1.5 per level
        var hp = mob.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (hp != null) {
            double base = Math.max(10.0, hp.getBaseValue());
            double scaled = base + 1.50 * (level - 1);
            hp.setBaseValue(scaled);
            // heal up to new max
            mob.setHealth((float) scaled);
        }

        // Slight movement boost for low-levels so they keep up
        var spd = mob.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (spd != null) {
            double base = Math.max(0.23, spd.getBaseValue());
            double bonus = Math.min(0.10, 0.003 * (level - 1)); // cap +0.10
            spd.setBaseValue(base + bonus);
        }
    }

    /** Check the villager -> villager_evo1 gate (level >= 7). Replace entity + update party entry. */
    private static void maybeEvolveActive(ServerWorld sw,
                                          ServerPlayerEntity owner,
                                          TamerState st,
                                          int partyIndex,
                                          PartyMember pm,
                                          MobEntity currentMob) {
        if (owner == null) return;

        // Only gate: vanilla villager id → evolve to your custom entity when level >= 7
        IdentifierIds ids = IdentifierIds.of(pm.entityTypeId);
        IdentifierIds villager = IdentifierIds.of(Registries.ENTITY_TYPE.getId(EntityType.VILLAGER));
        if (!ids.equals(villager)) return;
        if (pm.level < 7) return;

        // Update party entry type to evo1
        pm.entityTypeId = ModEntities.VILLAGER_EVO1_ID;
        st.markDirty();

        // Convert the live entity if possible (safer than despawn+spawn)
        MobEntity evolved = currentMob.convertTo(ModEntities.VILLAGER_EVO1, true);
        if (evolved == null) {
            // fallback: spawn a fresh one near the old spot
            evolved = ModEntities.VILLAGER_EVO1.create(sw);
            if (evolved != null) {
                evolved.refreshPositionAndAngles(currentMob.getX(), currentMob.getY(), currentMob.getZ(), currentMob.getYaw(), 0);
                sw.spawnEntity(evolved);
                currentMob.discard();
            } else {
                return; // couldn't evolve; bail quietly
            }
        }

        // Reinstall AI/level scaling/name
        TamerAI.install(evolved, owner);
        applyLevelTo(evolved, pm.level);
        evolved.setCustomName(Text.literal(pm.displayName() + "  Lv." + pm.level));
        evolved.setCustomNameVisible(true);

        // Track new active UUID
        st.setActive(owner.getUuid(), partyIndex, evolved.getUuid());
        st.markDirty();

        owner.sendMessage(Text.literal(pm.displayName() + " evolved!"), true);
    }

    /** Tiny helper to compare identifiers without dragging full Identifier in the public API. */
    private record IdentifierIds(String id) {
        static IdentifierIds of(net.minecraft.util.Identifier i) { return new IdentifierIds(i == null ? "" : i.toString()); }
        @Override public boolean equals(Object o) { return o instanceof IdentifierIds other && id.equals(other.id); }
    }
}
