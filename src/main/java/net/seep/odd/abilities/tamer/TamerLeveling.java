// net/seep/odd/abilities/tamer/TamerLeveling.java
package net.seep.odd.abilities.tamer;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.Registry;

import java.util.UUID;

/**
 * Awards XP to the active companion when it lands killing blows and
 * evolves Villager companions into 'odd:villager_evo' at level 7.
 */
public final class TamerLeveling {
    private TamerLeveling() {}

    // vanilla villager + your renamed evo id
    private static final Identifier VILLAGER_ID      = new Identifier("minecraft", "villager");
    private static final Identifier VILLAGER_EVO_ID  = new Identifier("odd",       "villager_evo");
    private static final int        VILLAGER_EVO_LVL = 7;

    /** Call once from Oddities.onInitialize(). */
    public static void initCommon() {
        ServerLivingEntityEvents.AFTER_DEATH.register((victim, source) -> {
            if (!(victim.getWorld() instanceof ServerWorld sw)) return;

            // who actually killed the victim?
            Entity rawAttacker = source.getAttacker();
            if (!(rawAttacker instanceof LivingEntity attacker)) return;

            // find the owner whose ACTIVE pet == attacker
            ServerPlayerEntity owner = findOwnerOfActive(sw, attacker.getUuid());
            if (owner == null) return;

            // check the owner's active slot & party member
            TamerState st = TamerState.get(sw);
            var active = st.getActive(owner.getUuid());
            if (active == null || !active.entity.equals(attacker.getUuid())) return;

            var party = st.partyOf(owner.getUuid());
            if (active.index < 0 || active.index >= party.size()) return;
            PartyMember pm = party.get(active.index);

            // grant XP
            int gained = expForKill(victim);
            pm.gainExp(gained);
            st.markDirty();

            // handle evolution(s)
            maybeEvolveVillager(sw, owner, attacker, pm, active.index);
        });
    }

    /* ---------------------- helpers ---------------------- */

    /** Simple owner lookup without touching private maps. */
    private static ServerPlayerEntity findOwnerOfActive(ServerWorld sw, UUID activeEntityId) {
        TamerState st = TamerState.get(sw);
        for (ServerPlayerEntity p : sw.getServer().getPlayerManager().getPlayerList()) {
            var a = st.getActive(p.getUuid());
            if (a != null && a.entity.equals(activeEntityId)) return p;
        }
        return null;
    }

    /** very rough XP curve: based on max health, bonus if hostile. */
    private static int expForKill(LivingEntity victim) {
        float base = Math.max(1f, victim.getMaxHealth());
        if (victim instanceof HostileEntity) base *= 1.5f;
        return Math.round(base);
    }

    /** Villager -> odd:villager_evo at level 7 using convertTo(). */
    private static void maybeEvolveVillager(ServerWorld sw,
                                            ServerPlayerEntity owner,
                                            LivingEntity attacker,
                                            PartyMember pm,
                                            int partyIndex) {
        if (!pm.entityTypeId.equals(VILLAGER_ID)) return;
        if (pm.level < VILLAGER_EVO_LVL) return;
        if (!(attacker instanceof MobEntity mob)) return;

        // resolve target type from the dynamic registry (no static Registries touch)
        Registry<EntityType<?>> reg = sw.getRegistryManager().get(RegistryKeys.ENTITY_TYPE);
        EntityType<?> raw = reg.get(VILLAGER_EVO_ID);
        if (raw == null) {
            owner.sendMessage(Text.literal("Evolution type missing: " + VILLAGER_EVO_ID), true);
            return;
        }

        @SuppressWarnings("unchecked")
        EntityType<? extends MobEntity> evoType = (EntityType<? extends MobEntity>) (EntityType<?>) raw;

        // convert in-place (keeps equipment)
        MobEntity evolved = mob.convertTo(evoType, true);
        if (evolved == null) {
            owner.sendMessage(Text.literal("Evolution failed (convert). Try re-summoning."), true);
            return;
        }

        // keep friendly AI + label
        evolved.setCustomName(Text.literal(pm.displayName() + "  Lv." + pm.level));
        evolved.setCustomNameVisible(true);
        TamerAI.install(evolved, owner);

        // update active reference + stored species
        TamerState st = TamerState.get(sw);
        st.setActive(owner.getUuid(), partyIndex, evolved.getUuid());
        pm.entityTypeId = VILLAGER_EVO_ID;
        st.markDirty();

        owner.sendMessage(Text.literal("✨ " + pm.displayName() + " evolved!"), true);
    }
}
