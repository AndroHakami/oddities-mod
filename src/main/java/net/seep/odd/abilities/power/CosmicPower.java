package net.seep.odd.abilities.power;

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
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import net.seep.odd.abilities.PowerAPI;
import net.seep.odd.abilities.cosmic.CosmicNet;
import net.seep.odd.abilities.cosmic.ability.DimensionalSlashAbility;
import net.seep.odd.abilities.cosmic.ability.OrbitingSwordsAbility;

import java.util.Map;
import java.util.UUID;

/**
 * Cosmic:
 *  PRIMARY: hold to stance (CPM "cosmic_stance"), release (or press again) = slash (CPM "cosmic_slash").
 *  SECONDARY: orbiting swords (unchanged).
 */
public final class CosmicPower implements Power, DeferredCooldownPower, SecondaryDuringCooldown {

    public static final CosmicPower INSTANCE = new CosmicPower();

    public static final int PRIMARY_COOLDOWN_TICKS   = 20 * 6;
    public static final int SECONDARY_COOLDOWN_TICKS = 20 * 8;
    public static final int STANCE_MAX_TICKS         = 14;

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
        return "Hold to charge a dimensional slash (CPM stance), release to blink and cleave. Orbiting swords on secondary.";
    }
    @Override public String slotLongDescription(String slot) {
        return switch (slot) {
            case "primary"   -> "Hold to charge; release (or press again) to dash. CPM stance/slash.";
            case "secondary" -> "Orbiting Swords: hold to hover 5 blades; release to volley; press during cooldown to recall.";
            default          -> "Cosmic";
        };
    }
    @Override public Identifier portraitTexture() { return new Identifier("odd", "textures/gui/overview/max_portrait.png"); }

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

    private static final DimensionalSlashAbility SLASH = new DimensionalSlashAbility();
    private static final OrbitingSwordsAbility   ORBIT = new OrbitingSwordsAbility();

    /* =================== inputs =================== */

    @Override
    public void activate(ServerPlayerEntity player) {
        if (!isCurrent(player)) return;
        State st = S(player);

        // Press again while charging = force release (safety if release packet is lost)
        if (st.primCharging) {
            st.primCharging = false;
            int held = Math.min(st.primHeldTicks, STANCE_MAX_TICKS);
            CosmicNet.sendCpmStance(player, false);          // stop stance
            SLASH.releaseAndSlash(player, held);
            CosmicNet.sendSlashPing(player);
            CosmicNet.sendCpmSlash(player, 10);              // ~0.5s hold on last frame
            PowerAPI.setHeld(player, "primary", false);
            PowerAPI.fire(player, "primary");
            return;
        }

        long rem = PowerAPI.getRemainingCooldownTicks(player, "primary");
        if (rem > 0) {
            player.sendMessage(Text.literal(String.format("Slash cooling: %.1fs", rem / 20.0)), true);
            return;
        }

        PowerAPI.beginUse(player, "primary");
        PowerAPI.setHeld(player, "primary", true);

        st.primCharging = true;
        st.primHeldTicks = 0;

        SLASH.beginCharge(player);
        CosmicNet.sendCpmStance(player, true);               // play "cosmic_stance"
    }

    /** Call this from your PRIMARY_RELEASE input. */
    public static void primaryRelease(ServerPlayerEntity player) {
        if (!isCurrent(player)) return;
        State st = S(player);
        if (!st.primCharging) return;

        st.primCharging = false;
        int held = Math.min(st.primHeldTicks, STANCE_MAX_TICKS);

        CosmicNet.sendCpmStance(player, false);              // stop "cosmic_stance"
        SLASH.releaseAndSlash(player, held);
        CosmicNet.sendSlashPing(player);
        CosmicNet.sendCpmSlash(player, 10);                  // play "cosmic_slash", hold a bit

        PowerAPI.setHeld(player, "primary", false);
        PowerAPI.fire(player, "primary");
    }

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        if (!isCurrent(player)) return;
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
                PowerAPI.fire(player, "secondary");
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
        State st = S(player);
        if (PowerAPI.getRemainingCooldownTicks(player, "secondary") > 0) return;
        if (ORBIT.isHovering(player) && !st.secFiredThisVolley) {
            st.secFiredThisVolley = true;
            ORBIT.releaseAndQueueFire(player);
            PowerAPI.setHeld(player, "secondary", false);
            PowerAPI.fire(player, "secondary");
        }
    }

    /* =================== per-tick =================== */
    public static void serverTick(ServerPlayerEntity p) {
        if (!isCurrent(p)) return;
        if (!(p.getWorld() instanceof ServerWorld)) return;

        State st = S(p);
        if (st.primCharging && st.primHeldTicks < STANCE_MAX_TICKS) {
            st.primHeldTicks++;
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

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> DATA.remove(handler.player.getUuid()));
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
}
