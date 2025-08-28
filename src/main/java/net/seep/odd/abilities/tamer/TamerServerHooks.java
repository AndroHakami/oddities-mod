// net/seep/odd/abilities/tamer/TamerServerHooks.java
package net.seep.odd.abilities.tamer;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
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

    /** Remove a party member (and despawn if active). */
    public static void handleKick(ServerPlayerEntity player, int index) {
        if (!(player.getWorld() instanceof ServerWorld sw)) return;
        TamerState st = TamerState.get(sw);
        var list = st.partyOf(player.getUuid());
        if (index < 0 || index >= list.size()) return;

        // if the active is this index, despawn it first
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

        // Despawn existing active
        var active = st.getActive(player.getUuid());
        if (active != null) {
            Entity old = sw.getEntity(active.entity);
            if (old != null) old.discard();
            st.clearActive(player.getUuid());
        }

        PartyMember pm = party.get(index);
        EntityType<?> type = Registries.ENTITY_TYPE.get(pm.entityTypeId); // runtime access
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

        // ✅ Clean nametag (no level text)
        le.setCustomName(Text.literal(pm.displayName()));
        le.setCustomNameVisible(true);

        sw.spawnEntity(le);
        st.setActive(player.getUuid(), index, le.getUuid());
        st.markDirty();
    }
}
