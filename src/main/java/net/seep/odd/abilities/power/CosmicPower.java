// src/main/java/net/seep/odd/abilities/power/CosmicPower.java
package net.seep.odd.abilities.power;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import net.seep.odd.abilities.PowerAPI;
import net.seep.odd.abilities.cosmic.CosmicNet;
import net.seep.odd.abilities.cosmic.CosmicFxNet;
import net.seep.odd.abilities.cosmic.ability.DimensionalSlashAbility;
import net.seep.odd.abilities.cosmic.ability.OrbitingSwordsAbility;

import net.seep.odd.abilities.data.CooldownState;
import net.seep.odd.abilities.net.PowerNetworking;

import net.seep.odd.item.ModItems;
import net.seep.odd.status.ModStatusEffects;

import java.util.Map;
import java.util.UUID;

public final class CosmicPower implements Power, DeferredCooldownPower, SecondaryDuringCooldown {

    public static final CosmicPower INSTANCE = new CosmicPower();

    public static final int PRIMARY_COOLDOWN_TICKS   = 20 * 6;
    public static final int SECONDARY_COOLDOWN_TICKS = 20 * 8;
    public static final int STANCE_MAX_TICKS         = 14;

    // Slowness 8 while charging (amp 7)
    private static final int CHARGE_SLOWNESS_AMP = 7;
    private static final int CHARGE_SLOWNESS_TICKS = 6; // refreshed often; falls off quickly after release

    @Override public String id() { return "cosmic"; }
    @Override public boolean hasSlot(String slot) { return "primary".equals(slot) || "secondary".equals(slot); }
    @Override public long cooldownTicks() { return PRIMARY_COOLDOWN_TICKS; }
    @Override public long secondaryCooldownTicks() { return SECONDARY_COOLDOWN_TICKS; }

    @Override public boolean deferPrimaryCooldown()   { return true; }
    @Override public boolean deferSecondaryCooldown() { return true; }
    @Override public boolean deferThirdCooldown()     { return false; }

    @Override
    public Identifier iconTexture(String slot) {
        return switch (slot) {
            case "primary"   -> new Identifier("odd", "textures/gui/abilities/cosmic_slash.png");
            case "secondary" -> new Identifier("odd", "textures/gui/abilities/cosmic_orbit.png");
            default          -> new Identifier("odd", "textures/gui/abilities/ability_default.png");
        };
    }

    @Override public String longDescription() {
        return "Cosmic lightning courses through your veins, cut through opponents with your godly katana, and break space itself with your slashes!";
    }

    @Override public String slotTitle(String slot) {
        return switch (slot) {
            case "primary" -> "DIMENSIONAL SLASH";
            case "secondary" -> "COSMIC SWORDS";
            default -> Power.super.slotTitle(slot);
        };
    }

    @Override public String slotLongDescription(String slot) {
        return switch (slot) {
            case "primary"   -> "While wielding the cosmic sword, press to charge up a powerful forward slash, then press again to release!";
            case "secondary" -> "Call upon 5 cosmic swords, press again to fire, press another time to recall the flying swords back to you.";
            default          -> "Cosmic";
        };
    }

    @Override public Identifier portraitTexture() {
        return new Identifier("odd", "textures/gui/overview/max_portrait.png");
    }

    private static final class State {
        boolean primCharging = false;
        int primHeldTicks = 0;
        boolean secFiredThisVolley = false;
    }

    private static final Map<UUID, State> DATA = new Object2ObjectOpenHashMap<>();
    private static State S(ServerPlayerEntity p) { return DATA.computeIfAbsent(p.getUuid(), u -> new State()); }

    private static boolean isCurrent(ServerPlayerEntity p) {
        Power pow = Powers.get(PowerAPI.get(p));
        return pow instanceof CosmicPower;
    }

    private static boolean hasKatanaInHand(ServerPlayerEntity p) {
        return p.getMainHandStack().isOf(ModItems.COSMIC_KATANA) || p.getOffHandStack().isOf(ModItems.COSMIC_KATANA);
    }

    private static final DimensionalSlashAbility SLASH = new DimensionalSlashAbility();
    private static final OrbitingSwordsAbility   ORBIT = new OrbitingSwordsAbility();

    /* =================== POWERLESS override =================== */

    private static final Object2LongOpenHashMap<UUID> WARN_UNTIL = new Object2LongOpenHashMap<>();

    private static boolean isPowerless(ServerPlayerEntity p) {
        return p != null && p.hasStatusEffect(ModStatusEffects.POWERLESS);
    }

    private static void warnOncePerSec(ServerPlayerEntity p, String msg) {
        long now = p.getWorld().getTime();
        long nextOk = WARN_UNTIL.getOrDefault(p.getUuid(), 0L);
        if (now < nextOk) return;
        WARN_UNTIL.put(p.getUuid(), now + 20);
        p.sendMessage(Text.literal(msg), true);
    }

    private static void cancelPrimaryCharge(ServerPlayerEntity player, State st) {
        if (player == null || st == null) return;
        if (!st.primCharging) return;

        st.primCharging = false;
        st.primHeldTicks = 0;

        CosmicNet.sendCpmStance(player, false);
        CosmicFxNet.sendChargeOverlay(player, false);

        PowerAPI.setHeld(player, "primary", false);
    }

    private static boolean denyPrimaryIfPowerless(ServerPlayerEntity player) {
        if (!isPowerless(player)) return false;
        cancelPrimaryCharge(player, DATA.get(player.getUuid()));
        warnOncePerSec(player, "§cYou are powerless.");
        return true;
    }

    private static boolean denySecondaryIfPowerless(ServerPlayerEntity player) {
        if (!isPowerless(player)) return false;
        PowerAPI.setHeld(player, "secondary", false);
        warnOncePerSec(player, "§cYou are powerless.");
        return true;
    }

    @Override
    public void forceDisable(ServerPlayerEntity player) {
        if (player == null) return;

        State st = DATA.get(player.getUuid());
        cancelPrimaryCharge(player, st);

        CosmicFxNet.sendChargeOverlay(player, false);

        ORBIT.retractAll(player);
        PowerAPI.setHeld(player, "secondary", false);

        if (st != null) st.secFiredThisVolley = false;
    }

    /* =================== inputs =================== */

    @Override
    public void activate(ServerPlayerEntity player) {
        if (!isCurrent(player)) return;
        if (denyPrimaryIfPowerless(player)) return;

        State st = S(player);

        // Press again while charging = release (ONLY if katana still in hand)
        if (st.primCharging) {
            if (!hasKatanaInHand(player)) {
                cancelPrimaryCharge(player, st);
                warnOncePerSec(player, "§cSlash canceled: hold the Cosmic Katana.");
                return;
            }

            st.primCharging = false;
            int held = Math.min(st.primHeldTicks, STANCE_MAX_TICKS);

            CosmicNet.sendCpmStance(player, false);
            CosmicFxNet.sendChargeOverlay(player, false);

            // Ability handles trail + teleport + rift (and re-checks katana server-side)
            SLASH.releaseAndSlash(player, held);

            CosmicNet.sendSlashPing(player);
            CosmicNet.sendCpmSlash(player, 10);
            PowerAPI.setHeld(player, "primary", false);
            startCooldown(player, "primary", PRIMARY_COOLDOWN_TICKS);
            return;
        }

        // Starting charge requires katana in hand
        if (!hasKatanaInHand(player)) {

            return;
        }

        long rem = PowerAPI.getRemainingCooldownTicks(player, "primary");
        if (rem > 0) {

            return;
        }

        PowerAPI.beginUse(player, "primary");
        PowerAPI.setHeld(player, "primary", true);

        st.primCharging = true;
        st.primHeldTicks = 0;

        // Slowness 8 immediately
        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.SLOWNESS,
                CHARGE_SLOWNESS_TICKS,
                CHARGE_SLOWNESS_AMP,
                true, false, false
        ));

        SLASH.beginCharge(player);

        CosmicNet.sendCpmStance(player, true);
        CosmicFxNet.sendChargeOverlay(player, true);
    }

    public static void primaryRelease(ServerPlayerEntity player) {
        if (!isCurrent(player)) return;
        if (denyPrimaryIfPowerless(player)) return;

        State st = S(player);
        if (!st.primCharging) return;

        // If katana not in hand, cancel instead of firing
        if (!hasKatanaInHand(player)) {
            cancelPrimaryCharge(player, st);
            warnOncePerSec(player, "§cSlash canceled: hold the Cosmic Katana.");
            return;
        }

        st.primCharging = false;
        int held = Math.min(st.primHeldTicks, STANCE_MAX_TICKS);

        CosmicNet.sendCpmStance(player, false);
        CosmicFxNet.sendChargeOverlay(player, false);

        SLASH.releaseAndSlash(player, held);

        CosmicNet.sendSlashPing(player);
        CosmicNet.sendCpmSlash(player, 10);

        PowerAPI.setHeld(player, "primary", false);
        startCooldown(player, "primary", PRIMARY_COOLDOWN_TICKS);
    }

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        if (!isCurrent(player)) return;
        if (denySecondaryIfPowerless(player)) return;

        State st = S(player);

        if (PowerAPI.getRemainingCooldownTicks(player, "secondary") > 0) {
            ORBIT.retractAll(player);
            return;
        }
        if (ORBIT.isHovering(player)) {
            if (!st.secFiredThisVolley) {
                st.secFiredThisVolley = true;
                ORBIT.releaseAndQueueFire(player);
                PowerAPI.setHeld(player, "secondary", false);
                startCooldown(player, "secondary", SECONDARY_COOLDOWN_TICKS);
            }
            return;
        }
        if (!ORBIT.retractAll(player)) {
            PowerAPI.beginUse(player, "secondary");
            PowerAPI.setHeld(player, "secondary", true);
            st.secFiredThisVolley = false;
            ORBIT.beginHover(player, 5);
        }
    }

    public static void secondaryHoldStart(ServerPlayerEntity player) {
        if (!isCurrent(player)) return;
        if (denySecondaryIfPowerless(player)) return;

        if (PowerAPI.getRemainingCooldownTicks(player, "secondary") > 0) {
            ORBIT.retractAll(player);
            return;
        }
        PowerAPI.beginUse(player, "secondary");
        PowerAPI.setHeld(player, "secondary", true);
        S(player).secFiredThisVolley = false;
        ORBIT.beginHover(player, 5);
    }

    public static void secondaryHoldEnd(ServerPlayerEntity player) {
        if (!isCurrent(player)) return;
        if (denySecondaryIfPowerless(player)) return;

        State st = S(player);
        if (PowerAPI.getRemainingCooldownTicks(player, "secondary") > 0) return;
        if (ORBIT.isHovering(player) && !st.secFiredThisVolley) {
            st.secFiredThisVolley = true;
            ORBIT.releaseAndQueueFire(player);
            PowerAPI.setHeld(player, "secondary", false);
            startCooldown(player, "secondary", SECONDARY_COOLDOWN_TICKS);
        }
    }

    /* =================== per-tick =================== */
    public static void serverTick(ServerPlayerEntity p) {
        if (!isCurrent(p)) return;
        if (!(p.getWorld() instanceof ServerWorld)) return;

        State st = S(p);

        if (isPowerless(p)) {
            cancelPrimaryCharge(p, st);
        } else {
            // Cancel charge if katana is no longer in hand
            if (st.primCharging && !hasKatanaInHand(p)) {
                cancelPrimaryCharge(p, st);
                warnOncePerSec(p, "§cSlash canceled: hold the Cosmic Katana.");
            }

            if (st.primCharging) {
                // refresh Slowness 8 while charging
                StatusEffectInstance cur = p.getStatusEffect(StatusEffects.SLOWNESS);
                if (cur == null || cur.getAmplifier() < CHARGE_SLOWNESS_AMP || cur.getDuration() <= 3) {
                    p.addStatusEffect(new StatusEffectInstance(
                            StatusEffects.SLOWNESS,
                            CHARGE_SLOWNESS_TICKS,
                            CHARGE_SLOWNESS_AMP,
                            true, false, false
                    ));
                }
            }

            if (st.primCharging && st.primHeldTicks < STANCE_MAX_TICKS) {
                st.primHeldTicks++;
            }
        }

        OrbitingSwordsAbility.serverTick(p);

        if (!ORBIT.isHovering(p) && !ORBIT.hasAnySwords(p)) {
            st.secFiredThisVolley = false;
        }
    }

    /* ========== guards ========== */
    static {
        AttackEntityCallback.EVENT.register((player, w, hand, entity, hit) -> {
            if (player instanceof ServerPlayerEntity sp && isCurrent(sp) && S(sp).primCharging) return ActionResult.FAIL;
            return ActionResult.PASS;
        });
        UseEntityCallback.EVENT.register((player, w, hand, entity, hit) -> {
            if (player instanceof ServerPlayerEntity sp && isCurrent(sp) && S(sp).primCharging) return ActionResult.FAIL;
            return ActionResult.PASS;
        });
        AttackBlockCallback.EVENT.register((player, w, hand, pos, dir) -> {
            if (player instanceof ServerPlayerEntity sp && isCurrent(sp) && S(sp).primCharging) return ActionResult.FAIL;
            return ActionResult.PASS;
        });
        UseBlockCallback.EVENT.register((player, w, hand, hit) -> {
            if (player instanceof ServerPlayerEntity sp && isCurrent(sp) && S(sp).primCharging) return ActionResult.FAIL;
            return ActionResult.PASS;
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.player.getUuid();
            DATA.remove(id);
            WARN_UNTIL.removeLong(id);
        });
    }

    /* ===== tiny client ping (unchanged) ===== */
    @Environment(EnvType.CLIENT)
    public static final class Client {
        private static int pingTicks = 0;
        public static void init() {
            HudRenderCallback.EVENT.register((DrawContext ctx, float td) -> {
                if (pingTicks > 0) {
                    pingTicks--;
                    MinecraftClient mc = MinecraftClient.getInstance();
                    int sw = mc.getWindow().getScaledWidth();
                    int sh = mc.getWindow().getScaledHeight();
                    int x = sw / 2, y = sh / 2;
                    int a = MathHelper.clamp(pingTicks * 16, 0, 0xAA);
                    ctx.fill(x - 2, y - 2, x + 2, y + 2, (a << 24) | 0x66FFFFFF);
                }
            });
        }
        public static void pingSlash() { pingTicks = 6; }
    }

    /* =================== local helper =================== */
    private static void startCooldown(ServerPlayerEntity player, String slot, int cooldownTicks) {
        String id = PowerAPI.get(player);
        if (id == null || id.isEmpty()) return;

        long now = player.getWorld().getTime();
        String key = id;
        String lane = "primary";

        switch (slot) {
            case "secondary" -> { key = id + "#secondary"; lane = "secondary"; }
            case "third"     -> { key = id + "#third";     lane = "third"; }
            case "fourth"    -> { key = id + "#fourth";    lane = "fourth"; }
            default -> { /* primary */ }
        }

        CooldownState.get(player.getServer()).setLastUse(player.getUuid(), key, now);
        PowerNetworking.sendCooldown(player, lane, Math.max(0, cooldownTicks));
    }
}