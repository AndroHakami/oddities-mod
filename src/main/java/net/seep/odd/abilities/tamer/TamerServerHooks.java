package net.seep.odd.abilities.tamer;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

public final class TamerServerHooks {
    private TamerServerHooks() {}

    public static void handleRename(ServerPlayerEntity player, int index, String name) {
        if (!(player.getWorld() instanceof ServerWorld sw)) return;
        TamerState st = TamerState.get(sw);
        st.renameMember(player.getUuid(), index, name);
        st.markDirty();
        net.seep.odd.abilities.net.TamerNet.sendOpenParty(player, st.partyOf(player.getUuid()));
    }

    /** Heal a party member. If it’s active, heal the entity; if not active, mark success (next spawn is full anyway). */
    public static void handleHeal(ServerPlayerEntity player, int index) {
        if (!(player.getWorld() instanceof ServerWorld sw)) return;

        TamerState st = TamerState.get(sw);
        var list = st.partyOf(player.getUuid());
        if (index < 0 || index >= list.size()) return;

        var pm = list.get(index);
        var active = st.getActive(player.getUuid());

        // If it’s currently summoned, heal the live entity
        if (active != null && active.index == index) {
            Entity e = sw.getEntity(active.entity);
            if (e instanceof LivingEntity le && le.isAlive()) {
                le.setHealth(le.getMaxHealth());
                player.sendMessage(Text.literal("Healed " + pm.displayName() + "!").formatted(Formatting.GREEN), true);
            } else {
                player.sendMessage(Text.literal("Couldn’t find an active summon to heal.").formatted(Formatting.RED), true);
            }
            return;
        }

        // Not active – acknowledge; (fresh spawns are full HP)
        player.sendMessage(Text.literal(pm.displayName() + " is ready at full health.").formatted(Formatting.AQUA), true);
    }

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
        // (No auto party UI open here)
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

    public static void handleSummonSelect(ServerPlayerEntity player, int index) {
        if (!(player.getWorld() instanceof ServerWorld sw)) return;
        TamerState st = TamerState.get(sw);
        var party = st.partyOf(player.getUuid());
        if (index < 0 || index >= party.size()) return;

        var active = st.getActive(player.getUuid());
        if (active != null) {
            Entity old = sw.getEntity(active.entity);
            if (old != null) old.discard();
            st.clearActive(player.getUuid());
        }

        PartyMember pm = party.get(index);
        EntityType<?> type = Registries.ENTITY_TYPE.get(pm.entityTypeId);
        Entity spawned = type.create(sw);
        if (!(spawned instanceof LivingEntity le)) {
            if (spawned != null) spawned.discard();
            return;
        }

        Vec3d spawn = player.getPos()
                .add(player.getRotationVec(1.0f).multiply(1.8))
                .add(0, 0.1, 0);
        le.refreshPositionAndAngles(spawn.x, spawn.y, spawn.z, player.getYaw(), 0);

        if (le instanceof net.minecraft.entity.mob.MobEntity mob) {
            TamerAI.install(mob, player);
        }

        // Keep nametag clean of level text
        le.setCustomName(Text.literal(pm.displayName()));
        le.setCustomNameVisible(true);

        sw.spawnEntity(le);
        st.setActive(player.getUuid(), index, le.getUuid());
        st.markDirty();
    }
}
