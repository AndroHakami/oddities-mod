package net.seep.odd.abilities;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.seep.odd.abilities.data.CooldownState;
import net.seep.odd.abilities.data.PowerState;
import net.seep.odd.abilities.net.PowerNetworking;
import net.seep.odd.abilities.power.DeferredCooldownPower;
import net.seep.odd.abilities.power.SecondaryDuringCooldown;
import net.seep.odd.abilities.power.Power;
import net.seep.odd.abilities.power.Powers;

import java.util.Map;
import java.util.UUID;

/**
 * Backwards-compatible PowerAPI with optional "defer cooldown until fire".
 * If a Power implements DeferredCooldownPower and returns true for a slot,
 * we DO NOT start cooldown in activate*(...), and expect the power to call
 * PowerAPI.fire(player, slot) when the action actually occurs.
 *
 * Non-deferred powers are unaffected.
 */
public final class PowerAPI {
    private PowerAPI() {}

    /* ================= in-use / held flags (lightweight, optional) ================= */

    private static final class UseFlags {
        boolean primaryHeld, secondaryHeld, thirdHeld;
        boolean primaryInUse, secondaryInUse, thirdInUse;
    }
    private static final Map<UUID, UseFlags> USE = new Object2ObjectOpenHashMap<>();
    private static UseFlags U(ServerPlayerEntity p) { return USE.computeIfAbsent(p.getUuid(), u -> new UseFlags()); }

    /** Mark that a slot has begun a "use" session (charging, etc.). */
    public static void beginUse(ServerPlayerEntity player, String slot) {
        UseFlags f = U(player);
        switch (slot) {
            case "primary"   -> f.primaryInUse = true;
            case "secondary" -> f.secondaryInUse = true;
            case "third"     -> f.thirdInUse = true;
        }
    }
    /** Mark that the button is being held (for UI/logic). */
    public static void setHeld(ServerPlayerEntity player, String slot, boolean held) {
        UseFlags f = U(player);
        switch (slot) {
            case "primary"   -> f.primaryHeld   = held;
            case "secondary" -> f.secondaryHeld = held;
            case "third"     -> f.thirdHeld     = held;
        }
    }
    /** Consumers if you need them. */
    public static boolean isHeld(ServerPlayerEntity p, String slot) {
        UseFlags f = U(p);
        return switch (slot) {
            case "primary" -> f.primaryHeld;
            case "secondary" -> f.secondaryHeld;
            case "third" -> f.thirdHeld;
            default -> false;
        };
    }
    public static boolean isInUse(ServerPlayerEntity p, String slot) {
        UseFlags f = U(p);
        return switch (slot) {
            case "primary" -> f.primaryInUse;
            case "secondary" -> f.secondaryInUse;
            case "third" -> f.thirdInUse;
            default -> false;
        };
    }

    /** Called by a power when the action actually "fires": applies cooldown now. */
    public static void fire(ServerPlayerEntity player, String slot) {
        String id = get(player);
        if (id == null || id.isEmpty()) return;
        Power power = Powers.get(id);
        if (power == null) return;

        long now = player.getWorld().getTime();
        String key = id;
        long cd = 0L;
        String lane = "primary";

        switch (slot) {
            case "secondary" -> { key = id + "#secondary"; cd = Math.max(0L, power.secondaryCooldownTicks()); lane = "secondary"; U(player).secondaryInUse = false; }
            case "third"     -> { key = id + "#third";     cd = Math.max(0L, power.thirdCooldownTicks());     lane = "third";     U(player).thirdInUse     = false; }
            default          -> {                            cd = Math.max(0L, power.cooldownTicks());         lane = "primary";   U(player).primaryInUse   = false; }
        }

        CooldownState.get(player.getServer()).setLastUse(player.getUuid(), key, now);
        PowerNetworking.sendCooldown(player, lane, cd);
    }

    /* ================= assignment & basic getters ================= */

    public static void set(ServerPlayerEntity player, String id, boolean callHook) {
        PowerState.get(player.getServer()).set(player.getUuid(), id);
        if (callHook && id != null && !id.isEmpty()) {
            Power p = Powers.get(id);
            if (p != null) p.onAssigned(player);
        }
        PowerNetworking.syncTo(player, id);
        // clear use flags on swap
        USE.remove(player.getUuid());
    }

    public static String get(ServerPlayerEntity player) {
        return PowerState.get(player.getServer()).get(player.getUuid());
    }

    public static boolean has(ServerPlayerEntity player) {
        String id = get(player);
        return id != null && !id.isEmpty();
    }

    public static void clear(ServerPlayerEntity player) {
        PowerState.get(player.getServer()).clear(player.getUuid());
        PowerNetworking.syncTo(player, "");
        USE.remove(player.getUuid());
    }

    /* ================= activations (with deferral, backward-compatible) ================= */

    /** Primary */
    public static void activate(ServerPlayerEntity player) {
        String id = get(player);
        if (id == null || id.isEmpty()) {
            player.sendMessage(Text.literal("You have no power assigned."), true);
            return;
        }
        Power power = Powers.get(id);
        if (power == null) {
            player.sendMessage(Text.literal("Unknown power: " + id), true);
            return;
        }

        long now = player.getWorld().getTime();
        long cd  = Math.max(0L, power.cooldownTicks());

        CooldownState cds = CooldownState.get(player.getServer());
        long last    = cds.getLastUse(player.getUuid(), id);
        long elapsed = now - last;

        boolean defer = (power instanceof DeferredCooldownPower d) && d.deferPrimaryCooldown();

        if (!defer && elapsed < cd) {
            long remaining = cd - elapsed;
            player.sendMessage(Text.literal(String.format("Cooling down: %.1fs", remaining / 20.0)), true);
            return;
        }

        power.activate(player);

        if (!defer) {
            cds.setLastUse(player.getUuid(), id, now);
            PowerNetworking.sendCooldown(player, "primary", cd);
        }
    }

    /** Secondary */
    public static void activateSecondary(ServerPlayerEntity player) {
        String id = get(player);
        if (id == null || id.isEmpty()) {
            player.sendMessage(Text.literal("You have no power assigned."), true);
            return;
        }
        Power power = Powers.get(id);
        if (power == null) {
            player.sendMessage(Text.literal("Unknown power: " + id), true);
            return;
        }

        long now = player.getWorld().getTime();
        long cd  = Math.max(0L, power.secondaryCooldownTicks());

        String cdKey = id + "#secondary";
        CooldownState cds = CooldownState.get(player.getServer());
        long last    = cds.getLastUse(player.getUuid(), cdKey);
        long elapsed = now - last;

        boolean defer = (power instanceof DeferredCooldownPower d) && d.deferSecondaryCooldown();

        // Allow certain powers to RECEIVE presses during cooldown (e.g., recall)
        if (elapsed < cd) {
            if (power instanceof SecondaryDuringCooldown) {
                power.activateSecondary(player); // power takes responsibility; no cooldown mutation here
                return;
            }
            long remaining = cd - elapsed;
            player.sendMessage(Text.literal(String.format("Secondary cooling: %.1fs", remaining / 20.0)), true);
            return;
        }

        power.activateSecondary(player);

        if (!defer) {
            cds.setLastUse(player.getUuid(), cdKey, now);
            PowerNetworking.sendCooldown(player, "secondary", cd);
        }
    }

    /** Third */
    public static void activateThird(ServerPlayerEntity player) {
        String id = get(player);
        if (id == null || id.isEmpty()) {
            player.sendMessage(Text.literal("You have no power assigned."), true);
            return;
        }
        Power power = Powers.get(id);
        if (power == null) {
            player.sendMessage(Text.literal("Unknown power: " + id), true);
            return;
        }

        long now = player.getWorld().getTime();
        long cd  = Math.max(0L, power.thirdCooldownTicks());

        String cdKey = id + "#third";
        CooldownState cds = CooldownState.get(player.getServer());
        long last    = cds.getLastUse(player.getUuid(), cdKey);
        long elapsed = now - last;

        boolean defer = (power instanceof DeferredCooldownPower d) && d.deferThirdCooldown();

        if (elapsed < cd && !defer) {
            long remaining = cd - elapsed;
            player.sendMessage(Text.literal(String.format("Third cooling: %.1fs", remaining / 20.0)), true);
            return;
        }

        power.activateThird(player);

        if (!defer) {
            cds.setLastUse(player.getUuid(), cdKey, now);
            PowerNetworking.sendCooldown(player, "third", cd);
        }
    }

    /* ================= cooldown queries ================= */

    /** Original helper kept for compatibility (primary slot). */
    public static long getRemainingCooldownTicks(ServerPlayerEntity player) {
        String id = get(player);
        if (id == null || id.isEmpty()) return 0L;
        Power p = Powers.get(id);
        if (p == null) return 0L;

        long now  = player.getWorld().getTime();
        long last = CooldownState.get(player.getServer()).getLastUse(player.getUuid(), id);
        long cd   = Math.max(0L, p.cooldownTicks());
        long rem  = cd - (now - last);
        return Math.max(0L, rem);
    }

    /** New overload: check "primary", "secondary", or "third". */
    public static long getRemainingCooldownTicks(ServerPlayerEntity player, String which) {
        String id = get(player);
        if (id == null || id.isEmpty()) return 0L;
        Power p = Powers.get(id);
        if (p == null) return 0L;

        long now = player.getWorld().getTime();
        String key = id;
        long cd = 0L;
        if ("secondary".equals(which)) {
            key = id + "#secondary"; cd = Math.max(0L, p.secondaryCooldownTicks());
        } else if ("third".equals(which)) {
            key = id + "#third";     cd = Math.max(0L, p.thirdCooldownTicks());
        } else {
            cd = Math.max(0L, p.cooldownTicks());
        }
        long last = CooldownState.get(player.getServer()).getLastUse(player.getUuid(), key);
        long rem  = cd - (now - last);
        return Math.max(0L, rem);
    }
}
