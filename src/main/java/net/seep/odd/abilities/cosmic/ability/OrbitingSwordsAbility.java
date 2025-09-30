package net.seep.odd.abilities.cosmic.ability;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.Vec3d;

import net.seep.odd.abilities.cosmic.entity.HomingCosmicSwordEntity;
import net.seep.odd.entity.ModEntities;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hold to hover 5 swords; on release, fire them sequentially (0.15s gap).
 */
public final class OrbitingSwordsAbility {
    private static final int MAX_SWORDS = 5;
    private static final int FIRE_INTERVAL_TICKS = 3; // 0.15s @ 20 TPS

    private static final class State {
        boolean hovering = false;
        final List<UUID> swordIds = new ObjectArrayList<>(MAX_SWORDS);

        // firing queue
        boolean firing = false;
        int nextFireTick = 0;
        int queueIndex = 0;
    }

    private static final Map<UUID, State> DATA = new Object2ObjectOpenHashMap<>();
    private static State S(ServerPlayerEntity p) { return DATA.computeIfAbsent(p.getUuid(), u -> new State()); }

    public boolean isHovering(ServerPlayerEntity p) {
        return S(p).hovering;
    }

    /** Begin hover: spawn 5 swords around the player. */
    public void beginHover(ServerPlayerEntity player, int count) {
        if (!(player.getWorld() instanceof ServerWorld sw)) return;
        State st = S(player);
        clearExisting(sw, st);

        st.hovering = true;
        st.firing = false;
        st.queueIndex = 0;
        st.swordIds.clear();

        int total = Math.min(Math.max(1, count), MAX_SWORDS);
        for (int i = 0; i < total; i++) {
            HomingCosmicSwordEntity e = new HomingCosmicSwordEntity(ModEntities.HOMING_COSMIC_SWORD, sw, player);
            e.beginHover(i, total); // set hover mode + orbit slot
            sw.spawnEntity(e);
            st.swordIds.add(e.getUuid());
        }
        sw.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 0.8f, 1.8f);
    }

    /** Release: start the sequential fire queue. */
    public void releaseAndQueueFire(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld sw)) return;
        State st = S(player);
        if (!st.hovering) return;

        st.hovering = false;
        st.firing = true;
        st.queueIndex = 0;
        st.nextFireTick = player.age; // fire immediately
        sw.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_CROSSBOW_LOADING_END, SoundCategory.PLAYERS, 0.6f, 1.4f);
    }

    /** Called every server tick from CosmicPower.serverTick(player). */
    public static void serverTick(ServerPlayerEntity p) {
        if (!(p.getWorld() instanceof ServerWorld sw)) return;
        State st = S(p);

        // Clean up if player is gone/dead
        if (!p.isAlive()) {
            clearExisting(sw, st);
            return;
        }

        // Sequential firing logic
        if (st.firing && p.age >= st.nextFireTick) {
            if (st.queueIndex < st.swordIds.size()) {
                UUID id = st.swordIds.get(st.queueIndex);
                var ent = sw.getEntity(id);
                if (ent instanceof HomingCosmicSwordEntity sword && sword.isAlive()) {
                    // Kick into SEEK mode toward current crosshair
                    Vec3d look = p.getRotationVec(1.0f).normalize();
                    sword.launch(look);
                    sw.playSound(null, sword.getX(), sword.getY(), sword.getZ(),
                            SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.5f, 1.7f);
                }
                st.queueIndex++;
                st.nextFireTick = p.age + FIRE_INTERVAL_TICKS;
            } else {
                // Done
                st.firing = false;
                st.swordIds.clear();
            }
        }
    }

    private static void clearExisting(ServerWorld sw, State st) {
        for (UUID id : st.swordIds) {
            var e = sw.getEntity(id);
            if (e instanceof HomingCosmicSwordEntity sword) sword.discard();
        }
        st.swordIds.clear();
        st.hovering = false;
        st.firing = false;
        st.queueIndex = 0;
    }
}
