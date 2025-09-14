// net/seep/odd/abilities/tamer/TamerServerHooks.java
package net.seep.odd.abilities.tamer;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class TamerServerHooks {
    private TamerServerHooks() {}

    public static void handleRename(ServerPlayerEntity player, int index, String name) {
        if (!(player.getWorld() instanceof ServerWorld sw)) return;
        var st = TamerState.get(sw);
        var list = st.partyOf(player.getUuid());
        if (index < 0 || index >= list.size()) return;
        list.get(index).nickname = sanitize(name);
        st.markDirty();
        net.seep.odd.abilities.net.TamerNet.sendOpenParty(player, list);
    }

    /** Called by TameBallEntity on successful capture. */
    public static void handleCapture(ServerPlayerEntity owner, LivingEntity target) {
        if (!(owner.getWorld() instanceof ServerWorld sw)) return;

        var st = TamerState.get(sw);
        if (st.partyOf(owner.getUuid()).size() >= TamerState.MAX_PARTY) {
            owner.sendMessage(Text.literal("Party is full!").formatted(Formatting.RED), true);
            return;
        }

        var typeId = Registries.ENTITY_TYPE.getId(target.getType());
        PartyMember pm = PartyMember.fromCapture(typeId, target);
        st.addMember(owner.getUuid(), pm);
        st.markDirty();

        owner.sendMessage(Text.literal("★ Captured ").formatted(Formatting.GOLD)
                .append(Text.literal(pm.displayName()).formatted(Formatting.YELLOW))
                .append(Text.literal("!").formatted(Formatting.GOLD)), true);

        target.discard();
    }

    public static void handleKick(ServerPlayerEntity player, int index) {
        if (!(player.getWorld() instanceof ServerWorld sw)) return;
        TamerState st = TamerState.get(sw);
        var list = st.partyOf(player.getUuid());
        if (index < 0 || index >= list.size()) return;

        var a = st.getActive(player.getUuid());
        if (a != null && a.index == index) {
            Entity old = sw.getEntity(a.entity);
            if (old != null) old.discard();
            st.clearActive(player.getUuid());
        }
        list.remove(index);
        st.markDirty();
        net.seep.odd.abilities.net.TamerNet.sendOpenParty(player, list);
    }

    public static void handleHeal(ServerPlayerEntity player, int index) {
        if (!(player.getWorld() instanceof ServerWorld sw)) return;
        TamerState st = TamerState.get(sw);
        var list = st.partyOf(player.getUuid());
        if (index < 0 || index >= list.size()) return;

        var pm = list.get(index);
        var a = st.getActive(player.getUuid());

        if (a != null && a.index == index) {
            Entity e = sw.getEntity(a.entity);
            if (e instanceof LivingEntity le && le.isAlive()) {
                le.setHealth(le.getMaxHealth());
                pm.hp = le.getHealth();
                pm.maxh = le.getMaxHealth();
                st.markDirty();
                player.sendMessage(Text.literal("Healed " + pm.displayName() + "!").formatted(Formatting.GREEN), true);
            } else {
                player.sendMessage(Text.literal("Couldn’t find an active summon to heal.").formatted(Formatting.RED), true);
            }
            return;
        }

        // Not active – set stored HP to max (or 20 if unknown)
        if (pm.maxh <= 0f) pm.maxh = 20f;
        pm.hp = pm.maxh;
        st.markDirty();
        player.sendMessage(Text.literal(pm.displayName() + " is ready at full health.").formatted(Formatting.AQUA), true);
    }

    /** Summon from wheel. Ensures safe spawn, per-level stats, HP floor(<20 only), restore HP, and spawn grace. */
    public static void handleSummonSelect(ServerPlayerEntity player, int index) {
        if (!(player.getWorld() instanceof ServerWorld sw)) return;
        TamerState st = TamerState.get(sw);
        var party = st.partyOf(player.getUuid());
        if (index < 0 || index >= party.size()) return;

        PartyMember pm = party.get(index);

        // If the creature is "fainted" (hp == 0), do not summon; ask player to heal.
        if (pm.hp == 0f) {
            player.sendMessage(Text.literal(pm.displayName() + " is fainted. Use heal in the party menu.").formatted(Formatting.RED), true);
            return;
        }

        // Despawn old
        var a = st.getActive(player.getUuid());
        if (a != null) {
            Entity old = sw.getEntity(a.entity);
            if (old != null) old.discard();
            st.clearActive(player.getUuid());
        }

        EntityType<?> type = Registries.ENTITY_TYPE.get(pm.entityTypeId);
        Entity spawned = type.create(sw);
        if (!(spawned instanceof LivingEntity le)) {
            if (spawned != null) spawned.discard();
            player.sendMessage(Text.literal("Couldn’t summon that creature.").formatted(Formatting.RED), true);
            return;
        }

        // Safe position behind owner; snap to ground
        Vec3d pos = findSafeSpawn(sw, player);
        le.refreshPositionAndAngles(pos.x, pos.y, pos.z, player.getYaw(), 0f);
        le.fallDistance = 0f;

        // Capture baselines on first summon
        if (pm.baseMaxH <= 0f) {
            var max = le.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
            if (max != null) pm.baseMaxH = (float)max.getBaseValue();
        }
        if (pm.baseAtk <= 0.0) {
            var atk = le.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
            if (atk != null) pm.baseAtk = atk.getBaseValue();
        }
        if (pm.baseSpd <= 0.0) {
            var spd = le.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
            if (spd != null) pm.baseSpd = spd.getBaseValue();
        }
        if (pm.baseDef <= 0.0) { // NEW
            var def = le.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_ARMOR);
            if (def != null) pm.baseDef = def.getBaseValue();
        }

        // Apply per-level stats (includes HP floor <20 only)
        TamerStats.applyOnSpawn(le, pm);

        // Restore saved HP if we have it; otherwise default to full
        float targetHp = le.getMaxHealth();
        if (pm.hp >= 0f) targetHp = Math.min(pm.hp, le.getMaxHealth());
        if (targetHp <= 0f) targetHp = Math.min(1f, le.getMaxHealth());
        le.setHealth(targetHp);

        // Mirror to party entry for UI
        pm.hp   = le.getHealth();
        pm.maxh = le.getMaxHealth();

        // Brief spawn grace (no burns/suffocation/fall; fire resist)
        le.extinguish();
        le.clearStatusEffects();
        le.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 200, 0, false, false));

        // Install AI (follow/protect/attack defaults; passive handled by NoAggroGoal)
        if (le instanceof net.minecraft.entity.mob.MobEntity mob) {
            TamerAI.install(mob, player);
        }

        // Clean nametag
        le.setCustomName(Text.literal(pm.displayName()));
        le.setCustomNameVisible(true);

        sw.spawnEntity(le);
        st.setActive(player.getUuid(), index, le.getUuid());
        st.setMode(player.getUuid(), TamerState.Mode.FOLLOW);
        st.markDirty();
    }

    public static void handleModeChange(ServerPlayerEntity player, int ordinal) {
        if (!(player.getWorld() instanceof ServerWorld sw)) return;
        TamerState st = TamerState.get(sw);
        var a = st.getActive(player.getUuid());
        if (a == null) return;

        TamerState.Mode[] modes = TamerState.Mode.values();
        TamerState.Mode mode = modes[Math.max(0, Math.min(modes.length - 1, ordinal))];
        a.mode = mode;
        st.markDirty();

        player.sendMessage(Text.literal("Companion mode: " + mode.name().toLowerCase()), true);
    }

    public static void handleRecall(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld sw)) return;
        TamerState st = TamerState.get(sw);
        var a = st.getActive(player.getUuid());
        if (a == null) return;

        Entity e = sw.getEntity(a.entity);
        if (e != null) e.discard();
        st.clearActive(player.getUuid());
        st.markDirty();
        player.sendMessage(Text.literal("Companion recalled."), true);
    }

    /* -------------------- helpers -------------------- */

    private static String sanitize(String s) {
        if (s == null) return "";
        s = s.strip();
        if (s.length() > 64) s = s.substring(0, 64);
        return s.replaceAll("[\\p{C}\\n\\r\\t]", "");
    }

    private static Vec3d findSafeSpawn(ServerWorld world, ServerPlayerEntity owner) {
        Vec3d back = owner.getRotationVec(1f).multiply(-1.5);
        Vec3d base = owner.getPos().add(back.x, 0.0, back.z);
        BlockPos.Mutable pos = new BlockPos.Mutable((int)Math.floor(base.x), (int)Math.floor(owner.getY()+0.5), (int)Math.floor(base.z));
        for (int dy = 3; dy >= -4; dy--) {
            BlockPos p = pos.mutableCopy().move(0, dy, 0);
            if (world.isAir(p) && world.isAir(p.up()) && !world.isAir(p.down())) {
                return new Vec3d(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
            }
        }
        return owner.getPos().add(-1, 0, -1);
    }
}
