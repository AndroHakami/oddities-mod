package net.seep.odd.abilities;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.seep.odd.abilities.data.CooldownState;
import net.seep.odd.abilities.data.PowerState;
import net.seep.odd.abilities.net.PowerNetworking;
import net.seep.odd.abilities.power.Power;
import net.seep.odd.abilities.power.Powers;

public final class PowerAPI {
    private PowerAPI() {}

    public static void set(ServerPlayerEntity player, String id, boolean callHook) {
        PowerState.get(player.getServer()).set(player.getUuid(), id);
        if (callHook && id != null && !id.isEmpty()) {
            Power p = Powers.get(id);
            if (p != null) p.onAssigned(player);
        }
        PowerNetworking.syncTo(player, id);
    }
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
        long cd = Math.max(0L, power.secondaryCooldownTicks());

        // store secondary cooldown under a distinct key
        String cdKey = id + "#secondary";
        CooldownState cds = CooldownState.get(player.getServer());
        long last = cds.getLastUse(player.getUuid(), cdKey);
        long elapsed = now - last;

        if (elapsed < cd) {
            long remaining = cd - elapsed;
            player.sendMessage(Text.literal(String.format("Secondary cooling: %.1fs", remaining / 20.0)), true);
            return;
        }

        power.activateSecondary(player);
        cds.setLastUse(player.getUuid(), cdKey, now);
        net.seep.odd.abilities.net.PowerNetworking.sendCooldown(player, "secondary", cd);
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
    }

    /** Call when a player tries to use their current power (primary). */
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

        long now = player.getWorld().getTime();          // server world time in ticks
        long cd = Math.max(0L, power.cooldownTicks());   // per-power cooldown

        CooldownState cds = CooldownState.get(player.getServer());
        long last = cds.getLastUse(player.getUuid(), id);
        long elapsed = now - last;

        if (elapsed < cd) {
            long remaining = cd - elapsed;
            double secs = remaining / 20.0;
            player.sendMessage(Text.literal(String.format("Cooling down: %.1fs", secs)), true); // actionbar-ish
            return;
        }

        // Use it!
        power.activate(player);
        cds.setLastUse(player.getUuid(), id, now);
        net.seep.odd.abilities.net.PowerNetworking.sendCooldown(player, "primary", cd);
    }

    /** Helper if you need it elsewhere */
    public static long getRemainingCooldownTicks(ServerPlayerEntity player) {
        String id = get(player);
        if (id == null || id.isEmpty()) return 0L;
        Power p = Powers.get(id);
        if (p == null) return 0L;

        long now = player.getWorld().getTime();
        long last = CooldownState.get(player.getServer()).getLastUse(player.getUuid(), id);
        long cd = Math.max(0L, p.cooldownTicks());
        long rem = cd - (now - last);
        return Math.max(0L, rem);
    }
}