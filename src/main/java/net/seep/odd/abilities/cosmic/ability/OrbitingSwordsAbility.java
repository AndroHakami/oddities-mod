package net.seep.odd.abilities.cosmic.ability;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

import net.seep.odd.abilities.cosmic.entity.HomingCosmicSwordEntity;
import net.seep.odd.entity.ModEntities;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Orbiting Swords:
 *  - beginHover: spawn N swords orbiting the player.
 *  - releaseAndQueueFire: fire them one-by-one, world-time gated, using ONE shared origin+dir snapshot.
 *  - retractAll: recall any NON-STUCK swords and despawn on arrival.
 */
public final class OrbitingSwordsAbility {
    private static final int MAX_SWORDS = 5;
    private static final int FIRE_INTERVAL_TICKS = 3;  // 0.15s
    private static final int START_DELAY_TICKS   = 3;  // 0.15s telegraph
    private static final double REJOIN_RADIUS_SQ = 0.80 * 0.80;

    private static final class State {
        boolean hovering = false;
        boolean fired = false; // debounce: prevent double-queue
        final List<UUID> swordIds = new ObjectArrayList<>(MAX_SWORDS);
        final Set<UUID> retracting = new ObjectOpenHashSet<>(MAX_SWORDS);
    }

    private static final Map<UUID, State> DATA = new Object2ObjectOpenHashMap<>();
    private static State S(ServerPlayerEntity p) { return DATA.computeIfAbsent(p.getUuid(), u -> new State()); }

    public boolean isHovering(ServerPlayerEntity p) { return S(p).hovering; }
    public boolean hasAnySwords(ServerPlayerEntity p) { State st = S(p); return !st.swordIds.isEmpty(); }

    public void beginHover(ServerPlayerEntity player, int count) {
        if (!(player.getWorld() instanceof ServerWorld sw)) return;
        State st = S(player);
        clearExisting(sw, st);

        int total = Math.min(Math.max(1, count), MAX_SWORDS);
        st.hovering = true;
        st.fired = false;
        st.swordIds.clear();
        st.retracting.clear();

        for (int i = 0; i < total; i++) {
            HomingCosmicSwordEntity e = new HomingCosmicSwordEntity(ModEntities.HOMING_COSMIC_SWORD, sw, player);
            e.beginHover(i, total);
            sw.spawnEntity(e);
            st.swordIds.add(e.getUuid());
        }

        sw.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 0.8f, 1.8f);
    }

    /** Debounced: will only queue once per hover. */
    public void releaseAndQueueFire(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld sw)) return;
        State st = S(player);
        if (!st.hovering || st.fired) return;

        st.hovering = false;
        st.fired = true; // debounce

        final var origin = player.getEyePos().add(0, -0.1, 0);
        final var dir    = player.getRotationVec(1.0f).normalize();

        for (int i = 0; i < st.swordIds.size(); i++) {
            UUID id = st.swordIds.get(i);
            var ent = sw.getEntity(id);
            if (ent instanceof HomingCosmicSwordEntity sword && sword.isAlive()) {
                int delay = START_DELAY_TICKS + i * FIRE_INTERVAL_TICKS;
                sword.scheduleLaunchWithSnapshot(origin, dir, delay);
            }
        }

        sw.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_CROSSBOW_LOADING_END, SoundCategory.PLAYERS, 0.6f, 1.4f);
    }

    /** Recall any NON-STUCK swords. */
    public boolean retractAll(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld sw)) return false;

        State st = S(player);
        boolean any = false;

        pruneDead(sw, st);

        for (UUID id : st.swordIds) {
            var ent = sw.getEntity(id);
            if (ent instanceof HomingCosmicSwordEntity sword && sword.isAlive()) {
                if (!sword.isStuck()) {
                    if (sword.startRetract()) {
                        st.retracting.add(id);
                        any = true;
                    }
                }
            }
        }

        if (any) {
            st.hovering = false;
            sw.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ITEM_TRIDENT_RETURN, SoundCategory.PLAYERS, 0.7f, 1.1f);
        }
        return any;
    }

    /** Cleanup + despawn-on-arrival for recalled swords. Call each server tick. */
    public static void serverTick(ServerPlayerEntity p) {
        if (!(p.getWorld() instanceof ServerWorld sw)) return;

        State st = DATA.get(p.getUuid());
        if (st == null) return;

        if (!p.isAlive()) {
            clearExisting(sw, st);
            return;
        }

        Iterator<UUID> it = st.swordIds.iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            var ent = sw.getEntity(id);

            if (!(ent instanceof HomingCosmicSwordEntity sword) || !sword.isAlive()) {
                it.remove();
                st.retracting.remove(id);
                continue;
            }

            if (st.retracting.contains(id)) {
                double distSq = sword.getPos().squaredDistanceTo(p.getEyePos().add(0, -0.1, 0));
                if (distSq <= REJOIN_RADIUS_SQ) {
                    sword.discard();
                    it.remove();
                    st.retracting.remove(id);
                    sw.playSound(null, p.getX(), p.getY(), p.getZ(),
                            SoundEvents.ITEM_TRIDENT_RETURN, SoundCategory.PLAYERS, 0.5f, 1.6f);
                }
            }
        }

        // Reset "fired" once the volley is gone (allows next hover to queue again)
        if (st.swordIds.isEmpty()) st.fired = false;
    }

    /* =============== helpers =============== */
    private static void clearExisting(ServerWorld sw, State st) {
        for (UUID id : st.swordIds) {
            var e = sw.getEntity(id);
            if (e instanceof HomingCosmicSwordEntity sword) sword.discard();
        }
        st.swordIds.clear();
        st.retracting.clear();
        st.hovering = false;
        st.fired = false;
    }

    private static void pruneDead(ServerWorld sw, State st) {
        st.swordIds.removeIf(id -> {
            var e = sw.getEntity(id);
            if (!(e instanceof HomingCosmicSwordEntity) || !e.isAlive()) {
                st.retracting.remove(id);
                return true;
            }
            return false;
        });
    }
}
