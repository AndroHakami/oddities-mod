// net/seep/odd/abilities/tamer/TamerLeveling.java
package net.seep.odd.abilities.tamer;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.seep.odd.abilities.net.TamerNet;

import java.util.UUID;

public final class TamerLeveling {
    private TamerLeveling() {}

    private static boolean registered = false;

    // evolution constants
    private static final Identifier VILLAGER_ID     = new Identifier("minecraft", "villager");
    private static final Identifier VILLAGER_EVO_ID = new Identifier("odd",       "villager_evo");
    private static final int VILLAGER_EVO_LVL = 6;
    private static volatile double XP_MULT = 1.0;   // change with command; 1.0 = normal

    public static void setXpMultiplier(double m) { XP_MULT = Math.max(0.0, m); }
    public static double getXpMultiplier() { return XP_MULT; }

    public static void register() {
        if (registered) return;
        registered = true;

        // 1) Grant XP when something dies from our pet (melee or projectile owner)
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((ServerWorld sw, Entity killer, LivingEntity victim) -> {
            LivingEntity attacker = extractLivingAttacker(killer);
            if (attacker == null) return;

            OwnerAndIndex oi = findOwnerOfActive(sw, attacker.getUuid());
            if (oi == null) return;

            TamerState st = TamerState.get(sw);
            var party = st.partyOf(oi.owner);
            if (oi.index < 0 || oi.index >= party.size()) return;

            PartyMember pm = party.get(oi.index);
            int gained = (int)Math.max(1, Math.round(expForKill(victim) * XP_MULT));
            pm.gainExp(gained);
            st.markDirty();

            maybeEvolveIfNeeded(sw, oi.owner, oi.index, pm);

            var owner = sw.getServer().getPlayerManager().getPlayer(oi.owner);
            if (owner != null) owner.sendMessage(Text.literal(pm.displayName() + " +" + gained + " XP"), true);
        });

        // 2) Push HUD (hp/xp/level) to owners every few ticks
        final int HUD_EVERY = 10;
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % HUD_EVERY != 0) return;

            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                ServerWorld sw = server.getOverworld();
                TamerState st = TamerState.get(sw);
                var a = st.getActive(p.getUuid());
                if (a == null) continue;

                var party = st.partyOf(p.getUuid());
                if (a.index < 0 || a.index >= party.size()) continue;

                PartyMember pm = party.get(a.index);
                Entity e = sw.getEntity(a.entity);

                float hp = 0f, maxHp = 0f;
                if (e instanceof LivingEntity le) {
                    hp = le.getHealth();
                    maxHp = le.getMaxHealth();
                }

                int level = pm.level;
                int exp   = pm.exp;
                int next  = Math.max(0, TamerXp.totalExpForLevel(level + 1) - exp);

                Identifier icon = new Identifier("odd", "textures/gui/tamer/icons/" + pm.entityTypeId.getPath() + ".png");
                TamerNet.sendHud(p, pm.displayName(), icon, hp, maxHp, level, exp, next);
            }
        });
    }

    // --- helpers ---

    private static LivingEntity extractLivingAttacker(Entity killer) {
        if (killer instanceof LivingEntity le) return le;
        if (killer instanceof ProjectileEntity proj && proj.getOwner() instanceof LivingEntity le) return le;
        return null;
    }

    private record OwnerAndIndex(UUID owner, int index) {}

    /** Scan online players’ 'active' to find who owns this entity UUID. */
    private static OwnerAndIndex findOwnerOfActive(ServerWorld sw, UUID entityUuid) {
        var pmgr = sw.getServer().getPlayerManager();
        TamerState st = TamerState.get(sw);
        for (ServerPlayerEntity p : pmgr.getPlayerList()) {
            var a = st.getActive(p.getUuid());
            if (a != null && a.entity.equals(entityUuid)) return new OwnerAndIndex(p.getUuid(), a.index);
        }
        return null;
    }

    private static int expForKill(LivingEntity victim) {
        int base = (victim instanceof LivingEntity) ? 12 : 5;
        int byHp = (int)Math.min(40, Math.max(1, victim.getMaxHealth() / 2f));
        return base + byHp;
    }

    /** villager -> villager_evo once level ≥ 7 */
    private static void maybeEvolveIfNeeded(ServerWorld sw, UUID ownerUuid, int partyIndex, PartyMember pm) {
        if (pm.level < VILLAGER_EVO_LVL) return;
        if (!pm.entityTypeId.equals(VILLAGER_ID)) return;

        TamerState st = TamerState.get(sw);
        var a = st.getActive(ownerUuid);
        if (a == null) return;

        Entity e = sw.getEntity(a.entity);
        if (!(e instanceof MobEntity mob)) return;

        var typeReg = sw.getRegistryManager().get(RegistryKeys.ENTITY_TYPE);
        EntityType<?> evoTypeRaw = typeReg.get(VILLAGER_EVO_ID);
        if (evoTypeRaw == null) return;

        @SuppressWarnings("unchecked")
        EntityType<? extends MobEntity> evoType = (EntityType<? extends MobEntity>) evoTypeRaw;

        MobEntity evolved = mob.convertTo(evoType, true);
        if (evolved == null) return;

        var owner = sw.getServer().getPlayerManager().getPlayer(ownerUuid);
        if (owner != null) TamerAI.install(evolved, owner);

        st.setActive(ownerUuid, partyIndex, evolved.getUuid());
        pm.entityTypeId = VILLAGER_EVO_ID;
        st.markDirty();

        if (owner != null) owner.sendMessage(Text.literal(pm.displayName() + " evolved!"), true);
    }
}
