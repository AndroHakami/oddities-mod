package net.seep.odd.abilities.tamer;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.seep.odd.abilities.net.TamerNet;

import java.util.UUID;

public final class TamerLeveling {
    private TamerLeveling() {}

    private static boolean registered = false;

    // Example evolution (customize or remove)
    private static final Identifier VILLAGER_ID     = new Identifier("minecraft", "villager");
    private static final Identifier VILLAGER_EVO_ID = new Identifier("odd",       "villager_evo");
    private static final int VILLAGER_EVO_LVL = 6;

    private static volatile double XP_MULT = 1.0;

    public static void setXpMultiplier(double m) { XP_MULT = Math.max(0.0, m); }
    public static double getXpMultiplier() { return XP_MULT; }

    /** Per-level XP cap (per level). */
    public static int nextFor(int level) { return Math.max(10, 10 + level * level * 5); }

    public static void register() {
        if (registered) return;
        registered = true;

        /* XP on pet kill */
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

            checkAndApply(sw, sw.getServer().getPlayerManager().getPlayer(oi.owner), st, oi.index);

            var owner = sw.getServer().getPlayerManager().getPlayer(oi.owner);
            if (owner != null) owner.sendMessage(Text.literal(pm.displayName() + " +" + gained + " XP"), true);
        });

        /* HUD push */
        final int HUD_EVERY = 10;
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % HUD_EVERY != 0) return;
            ServerWorld sw = server.getOverworld();
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                TamerState st = TamerState.get(sw);
                var a = st.getActive(p.getUuid());
                if (a == null) continue;

                var party = st.partyOf(p.getUuid());
                if (a.index < 0 || a.index >= party.size()) continue;

                PartyMember pm = party.get(a.index);
                Entity e = sw.getEntity(a.entity);

                float hp = 0f, maxHp = 0f;
                if (e instanceof LivingEntity le) { hp = le.getHealth(); maxHp = le.getMaxHealth(); }
                else { hp = pm.hp < 0 ? 0 : pm.hp; maxHp = pm.maxh < 0 ? 0 : pm.maxh; }

                int level  = pm.level;
                int exp    = pm.exp;
                int cap    = nextFor(level);

                Identifier icon = new Identifier("odd", "textures/gui/tamer/icons/" + pm.entityTypeId.getPath() + ".png");
                TamerNet.sendHud(p, pm.displayName(), icon, hp, maxHp, level, exp, cap);
            }
        });
    }

    /* ---------- helpers ---------- */

    private static LivingEntity extractLivingAttacker(Entity killer) {
        if (killer instanceof LivingEntity le) return le;
        if (killer instanceof ProjectileEntity proj && proj.getOwner() instanceof LivingEntity le) return le;
        return null;
    }

    private record OwnerAndIndex(UUID owner, int index) {}

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
        int base = 12;
        int byHp = (int)Math.min(40, Math.max(1, victim.getMaxHealth() / 2f));
        return base + byHp;
    }

    /** Consume XP into levels; re-apply stats to the active mob immediately; teach moves; maybe evolve. */
    public static void checkAndApply(ServerWorld sw, ServerPlayerEntity owner, TamerState st, int index) {
        if (owner == null) return;
        var party = st.partyOf(owner.getUuid());
        if (index < 0 || index >= party.size()) return;
        PartyMember pm = party.get(index);

        boolean leveled = false;
        while (pm.level < PartyMember.MAX_LEVEL && pm.exp >= nextFor(pm.level)) {
            pm.exp -= nextFor(pm.level);
            pm.level += 1;
            leveled = true;

            String learned = TamerMoves.learnMoveAtLevel(pm.entityTypeId, pm.level);
            if (learned != null) {
                boolean appended = TamerMoves.appendOrReplaceMove(pm, learned);
                owner.sendMessage(Text.literal(pm.displayName() + " learned " + TamerMoves.nameOf(learned) + (appended ? "!" : " (replaced oldest).")), false);
            }
        }

        if (!leveled) return;

        owner.sendMessage(Text.literal(pm.displayName() + " reached Lv." + pm.level + "!"), true);

        // If active: re-apply and show before→after numbers
        var a = st.getActive(owner.getUuid());
        if (a != null && a.index == index) {
            Entity e = sw.getEntity(a.entity);
            if (e instanceof MobEntity mob && mob.isAlive()) {
                float oldMax = mob.getMaxHealth();
                float oldHp  = mob.getHealth();
                double oldAtk = mob.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE) != null
                        ? mob.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE).getBaseValue() : 0.0;
                double oldSpd = mob.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED) != null
                        ? mob.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED).getBaseValue() : 0.0;

                // Apply scaled bases
                TamerStats.Scaled scaled = TamerStats.applyOnLevelUp(mob, pm);

                // Preserve HP ratio (or add delta if you prefer)
                float newMax = mob.getMaxHealth(); // reflects base we just set
                float ratio  = oldMax <= 0 ? 1f : Math.max(0.05f, oldHp / oldMax);
                mob.setHealth(Math.min(newMax, Math.max(1f, ratio * newMax)));

                // Mirror to PM for UI
                pm.hp   = mob.getHealth();
                pm.maxh = mob.getMaxHealth();

                owner.sendMessage(Text.literal(String.format(
                        "Stats: HP %.0f→%.0f  ATK %.1f→%.1f  SPD %.3f→%.3f",
                        (double)oldMax, (double)newMax, oldAtk, scaled.attack(), oldSpd, scaled.speed()
                )), false);
            }
        }

        maybeEvolveIfNeeded(sw, owner.getUuid(), index, pm);
        st.markDirty();
    }

    /* Example evolution (optional) */
    private static void maybeEvolveIfNeeded(ServerWorld sw, UUID ownerUuid, int partyIndex, PartyMember pm) {
        if (pm.level < VILLAGER_EVO_LVL || !pm.entityTypeId.equals(VILLAGER_ID)) return;

        TamerState st = TamerState.get(sw);
        var a = st.getActive(ownerUuid);

        if (a == null) { pm.entityTypeId = VILLAGER_EVO_ID; return; }

        Entity e = sw.getEntity(a.entity);
        if (!(e instanceof MobEntity mob)) { pm.entityTypeId = VILLAGER_EVO_ID; return; }

        EntityType<?> evoTypeRaw = Registries.ENTITY_TYPE.getOrEmpty(VILLAGER_EVO_ID).orElse(null);
        if (!(evoTypeRaw instanceof EntityType<?>)) { pm.entityTypeId = VILLAGER_EVO_ID; return; }
        @SuppressWarnings("unchecked")
        EntityType<? extends MobEntity> evoType = (EntityType<? extends MobEntity>) evoTypeRaw;

        float keepHp = (pm.hp >= 0 ? pm.hp : (mob.getHealth()));
        MobEntity evolved = mob.convertTo(evoType, true);
        if (evolved == null) { pm.entityTypeId = VILLAGER_EVO_ID; return; }

        var owner = sw.getServer().getPlayerManager().getPlayer(ownerUuid);
        if (owner != null) TamerAI.install(evolved, owner);

        // Re-apply per-level stats to the new form
        TamerStats.applyAfterEvolution(evolved, pm);

        float finalHp = Math.min(Math.max(1f, keepHp), evolved.getMaxHealth());
        evolved.setHealth(finalHp);

        st.setActive(ownerUuid, partyIndex, evolved.getUuid());
        pm.entityTypeId = VILLAGER_EVO_ID;
        pm.hp   = finalHp;
        pm.maxh = evolved.getMaxHealth();
    }
}
