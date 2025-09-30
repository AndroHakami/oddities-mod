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
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import net.seep.odd.abilities.PowerAPI;
import net.seep.odd.abilities.cosmic.ability.DimensionalSlashAbility;
import net.seep.odd.abilities.cosmic.ability.OrbitingSwordsAbility;
import net.seep.odd.abilities.cosmic.CosmicNet;

import java.util.Map;
import java.util.UUID;

/**
 * Cosmic power:
 *  - Primary: Dimensional Slash (short stance -> dash & line damage).
 *  - Secondary: Orbiting Swords (hold/prep spawns 5 hover swords; on release they fire sequentially).
 * No weapon requirement.
 */
public final class CosmicPower implements Power {

    /* ======================= config ======================= */
    public static final int PRIMARY_COOLDOWN_TICKS   = 20 * 6; // 6s
    public static final int SECONDARY_COOLDOWN_TICKS = 20 * 8; // 8s
    public static final int STANCE_MAX_TICKS         = 14;     // ~0.7s auto-fire fallback

    /* ======================= power meta ======================= */
    @Override public String id() { return "cosmic"; }
    @Override public boolean hasSlot(String slot) { return "primary".equals(slot) || "secondary".equals(slot); }
    @Override public long cooldownTicks() { return PRIMARY_COOLDOWN_TICKS; }
    @Override public long secondaryCooldownTicks() { return SECONDARY_COOLDOWN_TICKS; }

    @Override
    public Identifier iconTexture(String slot) {
        return switch (slot) {
            case "primary"   -> new Identifier("odd", "textures/gui/abilities/cosmic_slash.png");
            case "secondary" -> new Identifier("odd", "textures/gui/abilities/cosmic_orbit.png");
            default          -> new Identifier("odd", "textures/gui/abilities/ability_default.png");
        };
    }
    @Override public String longDescription() {
        return """
            Bend space with a dimensional slash, or surround yourself with orbiting blades and loose them one by one.
            """;
    }
    @Override public String slotLongDescription(String slot) {
        return switch (slot) {
            case "primary"   -> "Dimensional Slash: stance briefly, then dash and damage along a line.";
            case "secondary" -> "Orbiting Swords: hold to hover 5 blades; release to fire them in sequence.";
            default          -> "Cosmic";
        };
    }
    @Override public Identifier portraitTexture() {
        return new Identifier("odd", "textures/gui/overview/max_portrait.png");
    }

    /* ======================= per-player state ======================= */
    private static final class State {
        // PRIMARY (slash)
        boolean primCharging = false;
        int primHeldTicks = 0;

        // small HUD ping after slash
        int hudPingTicks = 0;
    }
    private static final Map<UUID, State> DATA = new Object2ObjectOpenHashMap<>();
    private static State S(ServerPlayerEntity p) { return DATA.computeIfAbsent(p.getUuid(), u -> new State()); }
    private static boolean isCurrent(ServerPlayerEntity p) {
        Power pow = Powers.get(PowerAPI.get(p));
        return pow instanceof CosmicPower;
    }

    private static final DimensionalSlashAbility SLASH = new DimensionalSlashAbility();
    private static final OrbitingSwordsAbility   ORBIT = new OrbitingSwordsAbility();
    public static final CosmicPower INSTANCE = new CosmicPower();

    /* ======================= inputs ======================= */
    /** PRIMARY pressed: begin stance (release will fire). */
    @Override
    public void activate(ServerPlayerEntity player) {
        if (!isCurrent(player)) return;
        State st = S(player);
        if (st.primCharging) return;

        st.primCharging = true;
        st.primHeldTicks = 0;
        SLASH.beginCharge(player); // CPM-safe
    }

    /** SECONDARY pressed:
     *  - If not hovering, start hover (spawn 5 blades).
     *  - If hovering, fire sequence now (fallback for systems without release events).
     */
    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        if (!isCurrent(player)) return;
        if (!ORBIT.isHovering(player)) {
            ORBIT.beginHover(player, 5);
        } else {
            ORBIT.releaseAndQueueFire(player); // toggle fallback
        }
    }

    /* ===== Optional hooks if your input system provides press/release events ===== */
    public static void primaryRelease(ServerPlayerEntity player) {
        if (!isCurrent(player)) return;
        State st = S(player);
        if (!st.primCharging) return;

        st.primCharging = false;
        SLASH.releaseAndSlash(player, st.primHeldTicks);
        CosmicNet.sendSlashPing(player);   // S2C HUD ping
        st.hudPingTicks = 6;
    }
    public static void secondaryHoldStart(ServerPlayerEntity player) {
        if (!isCurrent(player)) return;
        ORBIT.beginHover(player, 5);
    }
    public static void secondaryHoldEnd(ServerPlayerEntity player) {
        if (!isCurrent(player)) return;
        ORBIT.releaseAndQueueFire(player);
    }

    /* ======================= server tick ======================= */
    public static void serverTick(ServerPlayerEntity p) {
        if (!isCurrent(p)) return;
        if (!(p.getWorld() instanceof ServerWorld)) return;

        State st = S(p);

        // Primary (stance/auto-fire fallback)
        if (st.primCharging) {
            st.primHeldTicks++;
            if (st.primHeldTicks >= STANCE_MAX_TICKS) {
                st.primCharging = false;
                SLASH.releaseAndSlash(p, st.primHeldTicks);
                CosmicNet.sendSlashPing(p);   // S2C HUD ping
                st.hudPingTicks = 6;
            }
        }

        // Secondary (fire queue + maintenance)
        OrbitingSwordsAbility.serverTick(p);
    }

    /* =========== guards during stance (optional) =========== */
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

    /* ============== tiny client ping (optional/harmless) ============== */
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
