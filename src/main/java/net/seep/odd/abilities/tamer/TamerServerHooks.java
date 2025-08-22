// net/seep/odd/abilities/tamer/TamerServerHooks.java
package net.seep.odd.abilities.tamer;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.RegistryKeys;
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

    /**
     * Spawns the selected party member and makes it the active companion.
     * Resolves entity type via the world's registry manager (no static Registries access).
     */
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

        // Resolve type via runtime registry (avoids early static bootstrap)
        PartyMember pm = party.get(index);
        var entityRegistry = sw.getRegistryManager().get(RegistryKeys.ENTITY_TYPE);
        EntityType<?> type = entityRegistry.get(pm.entityTypeId); // may be null if mis-typed or not registered

        if (type == null) {
            player.sendMessage(Text.literal("Unknown entity type: " + pm.entityTypeId), true);
            return;
        }

        Entity spawned = type.create(sw);
        if (!(spawned instanceof LivingEntity le)) {
            if (spawned != null) spawned.discard();
            player.sendMessage(Text.literal("That type can't be summoned as a companion."), true);
            return;
        }

        // Spawn slightly in front of the player
        Vec3d spawn = player.getPos()
                .add(player.getRotationVec(1.0f).multiply(1.8))
                .add(0, 0.1, 0);
        le.refreshPositionAndAngles(spawn.x, spawn.y, spawn.z, player.getYaw(), 0);

        // Install companion AI (friendly + follow/defend)
        if (le instanceof net.minecraft.entity.mob.MobEntity mob) {
            TamerAI.install(mob, player);
        }

        // Nameplate with level
        le.setCustomName(Text.literal(pm.displayName() + "  Lv." + pm.level));
        le.setCustomNameVisible(true);

        // Spawn & record active
        sw.spawnEntity(le);
        st.setActive(player.getUuid(), index, le.getUuid());
        st.markDirty();
    }
}
