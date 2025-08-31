// net/seep/odd/abilities/tamer/TamerServerHooks.java
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
        // still refresh the wheel/party UI when you explicitly rename
        net.seep.odd.abilities.net.TamerNet.sendOpenParty(player, st.partyOf(player.getUuid()));
    }

    /**
     * Called after a successful capture (from TameBallEntity).
     * Adds to party, despawns the target, and shows a concise popup message.
     * DOES NOT auto-open the party manager anymore.
     */
    public static void handleCapture(ServerPlayerEntity owner, LivingEntity target) {
        if (!(owner.getWorld() instanceof ServerWorld sw)) return;

        TamerState st = TamerState.get(sw);
        if (st.partyOf(owner.getUuid()).size() >= TamerState.MAX_PARTY) {
            owner.sendMessage(Text.literal("Party is full!").formatted(Formatting.RED), true);
            return;
        }

        // Build the party member from the target
        var typeId = Registries.ENTITY_TYPE.getId(target.getType());
        PartyMember pm = PartyMember.fromCapture(typeId, target);

        // Add & persist
        st.addMember(owner.getUuid(), pm);
        st.markDirty();

        // Toast-like overlay (hotbar) + keep the big toast sound handled client-side in the ball logic
        owner.sendMessage(Text.literal("★ Captured ").formatted(Formatting.GOLD)
                .append(Text.literal(pm.displayName()).formatted(Formatting.YELLOW))
                .append(Text.literal("!").formatted(Formatting.GOLD)), true);

        // Remove the world entity last
        target.discard();

        // ⛔ No auto UI open here anymore:
        // net.seep.odd.abilities.net.TamerNet.sendOpenParty(owner, st.partyOf(owner.getUuid()));
    }

    /** Remove a party member (and despawn if active). */
    public static void handleKick(ServerPlayerEntity player, int index) {
        if (!(player.getWorld() instanceof ServerWorld sw)) return;
        TamerState st = TamerState.get(sw);
        var list = st.partyOf(player.getUuid());
        if (index < 0 || index >= list.size()) return;

        // Despawn if this was the active one
        var a = st.getActive(player.getUuid());
        if (a != null && a.index == index) {
            Entity old = sw.getEntity(a.entity);
            if (old != null) old.discard();
            st.clearActive(player.getUuid());
        }

        list.remove(index);
        st.markDirty();
        // Kicking is a deliberate party management action -> keep UI refresh
        net.seep.odd.abilities.net.TamerNet.sendOpenParty(player, list);
    }

    /** Summon the selected party member next to the player with friendly AI installed. */
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

        // Spawn the new companion
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
            // Base follow/protect + species-specific combat behaviors are installed here
            TamerAI.install(mob, player);
        }

        // Clean nametag (no level text on the entity)
        le.setCustomName(Text.literal(pm.displayName()));
        le.setCustomNameVisible(true);

        sw.spawnEntity(le);
        st.setActive(player.getUuid(), index, le.getUuid());
        st.markDirty();
    }
}
