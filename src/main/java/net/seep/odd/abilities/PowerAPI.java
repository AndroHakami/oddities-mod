// src/main/java/net/seep/odd/abilities/PowerAPI.java
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

// charge + hold/release
import net.seep.odd.abilities.data.ChargeState;
import net.seep.odd.abilities.power.ChargedPower;
import net.seep.odd.abilities.power.HoldReleasePower;

import java.util.Map;
import java.util.UUID;

/**
 * Backwards-compatible PowerAPI with optional "defer cooldown until fire".
 * Non-deferred powers behave the same as before.
 *
 * Extended: charge-based slots + hold/release hooks + latch to block "hold to bypass cooldown".
 */
public final class PowerAPI {
    private PowerAPI() {}

    /* ================= in-use / held flags (lightweight, optional) ================= */

    private static final class UseFlags {
        boolean primaryHeld, secondaryHeld, thirdHeld, fourthHeld;
        boolean primaryInUse, secondaryInUse, thirdInUse, fourthInUse;

        // Latch = this hold session was permitted at holdStart (not cooling then).
        boolean primaryLatch, secondaryLatch, thirdLatch, fourthLatch;
    }
    private static final Map<UUID, UseFlags> USE = new Object2ObjectOpenHashMap<>();
    private static UseFlags U(ServerPlayerEntity p) { return USE.computeIfAbsent(p.getUuid(), u -> new UseFlags()); }

    private static void setLatch(ServerPlayerEntity p, String slot, boolean v) {
        UseFlags f = U(p);
        switch (slot) {
            case "primary"   -> f.primaryLatch   = v;
            case "secondary" -> f.secondaryLatch = v;
            case "third"     -> f.thirdLatch     = v;
            case "fourth"    -> f.fourthLatch    = v;
        }
    }
    private static boolean getLatch(ServerPlayerEntity p, String slot) {
        UseFlags f = U(p);
        return switch (slot) {
            case "primary"   -> f.primaryLatch;
            case "secondary" -> f.secondaryLatch;
            case "third"     -> f.thirdLatch;
            case "fourth"    -> f.fourthLatch;
            default -> false;
        };
    }

    /** Mark that a slot has begun a "use" session (charging, etc.). */
    public static void beginUse(ServerPlayerEntity player, String slot) {
        UseFlags f = U(player);
        switch (slot) {
            case "primary"   -> f.primaryInUse   = true;
            case "secondary" -> f.secondaryInUse = true;
            case "third"     -> f.thirdInUse     = true;
            case "fourth"    -> f.fourthInUse    = true;
        }
    }
    /** Mark that the button is being held (for UI/logic). */
    public static void setHeld(ServerPlayerEntity player, String slot, boolean held) {
        UseFlags f = U(player);
        switch (slot) {
            case "primary"   -> f.primaryHeld   = held;
            case "secondary" -> f.secondaryHeld = held;
            case "third"     -> f.thirdHeld     = held;
            case "fourth"    -> f.fourthHeld    = held;
        }
    }

    // --- helpers for per-slot cooldowns ---
    private static long cooldownTicksFor(Power p, String slot) {
        return switch (slot) {
            case "secondary" -> Math.max(0L, p.secondaryCooldownTicks());
            case "third"     -> Math.max(0L, p.thirdCooldownTicks());
            case "fourth"    -> Math.max(0L, p.fourthCooldownTicks());
            default          -> Math.max(0L, p.cooldownTicks()); // primary
        };
    }

    private static String cooldownKeyFor(String id, String slot) {
        return switch (slot) {
            case "secondary" -> id + "#secondary";
            case "third"     -> id + "#third";
            case "fourth"    -> id + "#fourth";
            default          -> id; // primary
        };
    }

    public static boolean isHeld(ServerPlayerEntity p, String slot) {
        UseFlags f = U(p);
        return switch (slot) {
            case "primary"   -> f.primaryHeld;
            case "secondary" -> f.secondaryHeld;
            case "third"     -> f.thirdHeld;
            case "fourth"    -> f.fourthHeld;
            default -> false;
        };
    }
    public static boolean isInUse(ServerPlayerEntity p, String slot) {
        UseFlags f = U(p);
        return switch (slot) {
            case "primary"   -> f.primaryInUse;
            case "secondary" -> f.secondaryInUse;
            case "third"     -> f.thirdInUse;
            case "fourth"    -> f.fourthInUse;
            default -> false;
        };
    }

    /* ================= HOLD / RELEASE (additive hooks) ================= */

    /** Called when a slot starts being held (C2S). Latches the session if not cooling at press time. */
    public static void holdStart(ServerPlayerEntity player, String slot) {
        String id = get(player);
        if (id == null || id.isEmpty()) return;
        Power p = Powers.get(id);
        if (p == null) return;

        // Gate: do not allow a hold to begin if slot is cooling.
        long rem = getRemainingCooldownTicks(player, slot);
        if (rem > 0) {
            player.sendMessage(Text.literal(String.format("Cooling: %.1fs", rem / 20.0)), true);
            setHeld(player, slot, false);
            setLatch(player, slot, false);
            return;
        }

        // Allowed now; latch this session so it cannot "wait out" the cooldown.
        setLatch(player, slot, true);

        if (p instanceof HoldReleasePower hr) hr.onHoldStart(player, slot);
        beginUse(player, slot);
        setHeld(player, slot, true);
    }

    /** Called periodically while held (C2S or server ticking). Ignored if the session wasn't latched. */
    public static void holdTick(ServerPlayerEntity player, String slot, int heldTicks) {
        String id = get(player);
        if (id == null || id.isEmpty()) return;
        Power p = Powers.get(id);
        if (p == null) return;

        // If currently cooling or session wasn't latched, ignore.
        if (getRemainingCooldownTicks(player, slot) > 0 || !getLatch(player, slot)) return;

        if (p instanceof HoldReleasePower hr) hr.onHoldTick(player, slot, heldTicks);
    }

    /** Called on release (C2S). Applies cooldown at release for held slots. */
    public static void holdRelease(ServerPlayerEntity player, String slot, int heldTicks, boolean canceled) {
        String id = get(player);
        if (id == null || id.isEmpty()) return;
        Power p = Powers.get(id);
        if (p == null) return;

        // clear held/in-use flags
        setHeld(player, slot, false);
        switch (slot) {
            case "primary"   -> U(player).primaryInUse   = false;
            case "secondary" -> U(player).secondaryInUse = false;
            case "third"     -> U(player).thirdInUse     = false;
            case "fourth"    -> U(player).fourthInUse    = false;
        }

        boolean latched = getLatch(player, slot);
        setLatch(player, slot, false);

        // If the session started during cooldown, or was canceled, do nothing.
        if (canceled || !latched) return;

        // If cooldown started during the hold (e.g., externally), block firing.
        if (getRemainingCooldownTicks(player, slot) > 0) {
            player.sendMessage(Text.literal("Cooling."), true);
            return;
        }

        if (p instanceof HoldReleasePower hr) hr.onHoldRelease(player, slot, heldTicks, false);

        // Apply cooldown now for held slots.
        long cd = cooldownTicksFor(p, slot);
        if (cd > 0) {
            long now = player.getWorld().getTime();
            String key = cooldownKeyFor(id, slot);
            CooldownState.get(player.getServer()).setLastUse(player.getUuid(), key, now);
            PowerNetworking.sendCooldown(player, slot, cd);
        }
    }

    /* ================= assignment & basic getters ================= */

    public static void set(ServerPlayerEntity player, String id, boolean callHook) {
        PowerState.get(player.getServer()).set(player.getUuid(), id);
        if (callHook && id != null && !id.isEmpty()) {
            Power p = Powers.get(id);
            if (p != null) p.onAssigned(player);
        }
        // Sends id AND (now) charge snapshots for any charge slots
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

    /* ================== charge helpers (additive) ================== */

    private static boolean slotUsesCharges(Power power, String slot) {
        return (power instanceof ChargedPower cp) && cp.usesCharges(slot);
    }

    /** Try to consume a charge for this slot; returns true if consumed and schedules its lane recharge. */
    private static boolean tryConsumeCharge(ServerPlayerEntity player, Power power, String id, String slot) {
        if (!(power instanceof ChargedPower cp) || !cp.usesCharges(slot)) return true; // not charge-based

        int  max = Math.max(1, cp.maxCharges(slot));
        long now = player.getWorld().getTime();
        String key = id + "#" + slot;

        // lazy regen on read
        ChargeState cs = ChargeState.get(player.getServer());
        cs.tick(key, player.getUuid(), max, now);

        int have = cs.get(key, player.getUuid(), max);
        if (have <= 0) {
            player.sendMessage(Text.literal("No charges."), true);
            return false;
        }

        long rt = Math.max(0L, cp.rechargeTicks(slot));
        cs.consume(key, player.getUuid(), max, now, rt);

        // send fresh snapshot so HUD drops immediately and knows nextReady
        ChargeState.Snapshot snap = cs.snapshot(key, player.getUuid(), max, now);
        PowerNetworking.sendCharges(player, slot, snap.have(), snap.max(), snap.recharge(), snap.nextReady(), snap.now());

        return true;
    }

    /* ================= activations (with charges + deferral) ================= */

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

        // Charge-based primary: consume one and run without cooldown
        if (slotUsesCharges(power, "primary")) {
            if (!tryConsumeCharge(player, power, id, "primary")) return;
            power.activate(player);
            return;
        }

        // Original cooldown path
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

        // Charge-based secondary
        if (slotUsesCharges(power, "secondary")) {
            if (!tryConsumeCharge(player, power, id, "secondary")) return;
            power.activateSecondary(player);
            return;
        }

        long now = player.getWorld().getTime();
        long cd  = Math.max(0L, power.secondaryCooldownTicks());

        String cdKey = id + "#secondary";
        CooldownState cds = CooldownState.get(player.getServer());
        long last    = cds.getLastUse(player.getUuid(), cdKey);
        long elapsed = now - last;

        boolean defer = (power instanceof DeferredCooldownPower d) && d.deferSecondaryCooldown();

        if (elapsed < cd) {
            if (power instanceof SecondaryDuringCooldown) {
                power.activateSecondary(player);
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

        // Charge-based third
        if (slotUsesCharges(power, "third")) {
            if (!tryConsumeCharge(player, power, id, "third")) return;
            power.activateThird(player);
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

    /** Fourth */
    public static void activateFourth(ServerPlayerEntity player) {
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

        // Charge-based fourth
        if (slotUsesCharges(power, "fourth")) {
            if (!tryConsumeCharge(player, power, id, "fourth")) return;
            power.activateFourth(player);
            return;
        }

        long now = player.getWorld().getTime();
        long cd  = Math.max(0L, power.fourthCooldownTicks());

        String cdKey = id + "#fourth";
        CooldownState cds = CooldownState.get(player.getServer());
        long last    = cds.getLastUse(player.getUuid(), cdKey);
        long elapsed = now - last;

        if (elapsed < cd) {
            long remaining = cd - elapsed;
            player.sendMessage(Text.literal(String.format("Fourth cooling: %.1fs", remaining / 20.0)), true);
            return;
        }

        power.activateFourth(player);

        cds.setLastUse(player.getUuid(), cdKey, now);
        PowerNetworking.sendCooldown(player, "fourth", cd);
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

    /** New overload: "primary", "secondary", "third", "fourth". */
    public static long getRemainingCooldownTicks(ServerPlayerEntity player, String which) {
        String id = get(player);
        if (id == null || id.isEmpty()) return 0L;
        Power p = Powers.get(id);
        if (p == null) return 0L;

        long now = player.getWorld().getTime();
        String key = id;
        long cd;
        if ("secondary".equals(which)) {
            key = id + "#secondary"; cd = Math.max(0L, p.secondaryCooldownTicks());
        } else if ("third".equals(which)) {
            key = id + "#third";     cd = Math.max(0L, p.thirdCooldownTicks());
        } else if ("fourth".equals(which)) {
            key = id + "#fourth";    cd = Math.max(0L, p.fourthCooldownTicks());
        } else {
            cd = Math.max(0L, p.cooldownTicks());
        }
        long last = CooldownState.get(player.getServer()).getLastUse(player.getUuid(), key);
        long rem  = cd - (now - last);
        return Math.max(0L, rem);
    }
}
