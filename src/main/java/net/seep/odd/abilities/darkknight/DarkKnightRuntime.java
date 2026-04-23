package net.seep.odd.abilities.darkknight;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.seep.odd.entity.darkknight.DarkShieldEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side runtime state for Dark Knight.
 *
 * Tracks:
 * - stored shield HP while recalled
 * - whether the shield is recharging
 * - active shield entity for each owner
 * - which target is currently being protected
 * - the passive cleave window after shield break
 */
public final class DarkKnightRuntime {
    private DarkKnightRuntime() {}

    public static final float MAX_SHIELD_HEALTH = 12.0F; // 6 hearts
    public static final float REGEN_PER_TICK = 0.05F;    // 1 HP / second while recalled
    public static final int SHIELD_UPTIME_TICKS = 20 * 15;
    public static final int PASSIVE_WINDOW_TICKS = 20 * 2;

    private static final Map<UUID, ShieldState> STATES = new HashMap<>();
    private static final Map<UUID, UUID> OWNER_TO_SHIELD = new HashMap<>();
    private static final Map<UUID, UUID> PROTECTED_TO_OWNER = new HashMap<>();
    private static final Map<UUID, Long> CLEAVE_UNTIL = new HashMap<>();

    public static ShieldState refresh(UUID ownerUuid, long worldTick) {
        ShieldState state = STATES.computeIfAbsent(ownerUuid, uuid -> new ShieldState());

        if (state.lastTick == Long.MIN_VALUE) {
            state.lastTick = worldTick;
            return state;
        }

        if (state.recharging && state.health < MAX_SHIELD_HEALTH) {
            long elapsed = Math.max(0L, worldTick - state.lastTick);
            if (elapsed > 0L) {
                state.health = Math.min(MAX_SHIELD_HEALTH, state.health + (elapsed * REGEN_PER_TICK));
                if (state.health >= MAX_SHIELD_HEALTH - 0.001F) {
                    state.health = MAX_SHIELD_HEALTH;
                    state.recharging = false;
                }
            }
        }

        state.lastTick = worldTick;
        return state;
    }

    public static void beginActive(UUID ownerUuid, UUID shieldUuid, long worldTick) {
        ShieldState state = refresh(ownerUuid, worldTick);
        state.recharging = false;
        OWNER_TO_SHIELD.put(ownerUuid, shieldUuid);
    }

    public static void stopActive(UUID ownerUuid, UUID protectedUuid, float storedHealth, long worldTick) {
        OWNER_TO_SHIELD.remove(ownerUuid);

        if (protectedUuid != null) {
            UUID mappedOwner = PROTECTED_TO_OWNER.get(protectedUuid);
            if (ownerUuid.equals(mappedOwner)) {
                PROTECTED_TO_OWNER.remove(protectedUuid);
            }
        }

        ShieldState state = refresh(ownerUuid, worldTick);
        state.health = Math.max(0.0F, Math.min(MAX_SHIELD_HEALTH, storedHealth));
        state.recharging = true;
        state.lastTick = worldTick;
    }

    public static void setProtected(UUID ownerUuid, UUID oldProtectedUuid, UUID newProtectedUuid) {
        if (oldProtectedUuid != null) {
            UUID mappedOwner = PROTECTED_TO_OWNER.get(oldProtectedUuid);
            if (ownerUuid.equals(mappedOwner)) {
                PROTECTED_TO_OWNER.remove(oldProtectedUuid);
            }
        }
        if (newProtectedUuid != null) {
            PROTECTED_TO_OWNER.put(newProtectedUuid, ownerUuid);
        }
    }

    public static DarkShieldEntity getShieldForOwner(ServerWorld world, UUID ownerUuid) {
        UUID shieldUuid = OWNER_TO_SHIELD.get(ownerUuid);
        if (shieldUuid == null) {
            return null;
        }

        if (!(world.getEntity(shieldUuid) instanceof DarkShieldEntity shield) || !shield.isAlive()) {
            OWNER_TO_SHIELD.remove(ownerUuid);
            return null;
        }

        return shield;
    }

    public static DarkShieldEntity getShieldProtecting(ServerWorld world, UUID protectedUuid) {
        UUID ownerUuid = PROTECTED_TO_OWNER.get(protectedUuid);
        if (ownerUuid == null) {
            return null;
        }

        DarkShieldEntity shield = getShieldForOwner(world, ownerUuid);
        if (shield == null) {
            PROTECTED_TO_OWNER.remove(protectedUuid);
        }
        return shield;
    }

    public static void armCleave(UUID ownerUuid, long untilTick) {
        CLEAVE_UNTIL.put(ownerUuid, untilTick);
    }

    public static boolean consumeCleave(ServerPlayerEntity player) {
        Long untilTick = CLEAVE_UNTIL.get(player.getUuid());
        if (untilTick == null) {
            return false;
        }

        long now = player.getWorld().getTime();
        if (now > untilTick) {
            CLEAVE_UNTIL.remove(player.getUuid());
            return false;
        }

        CLEAVE_UNTIL.remove(player.getUuid());
        return true;
    }

    public static final class ShieldState {
        public float health = MAX_SHIELD_HEALTH;
        public boolean recharging = false;
        public long lastTick = Long.MIN_VALUE;
    }
}
