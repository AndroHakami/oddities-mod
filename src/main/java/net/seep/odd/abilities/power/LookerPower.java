package net.seep.odd.abilities.power;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import net.seep.odd.abilities.looker.LookerNet;
import net.seep.odd.abilities.looker.OddLookerInvisibility;
import net.seep.odd.sound.ModSounds;
import net.seep.odd.status.ModStatusEffects;

import virtuoel.pehkui.api.ScaleData;
import virtuoel.pehkui.api.ScaleTypes;

import java.util.Map;
import java.util.UUID;

public final class LookerPower implements Power, ChargedPower {

    /* ---------- config ---------- */
    private static final double BLINK_MAX_DIST       = 10.0;

    private static final int    BLINK_MAX_CHARGES    = 3;
    private static final int    BLINK_RECHARGE_T     = 20 * 6;

    private static final int    INVIS_MAX_TICKS      = 20 * 8;
    private static final int    INVIS_DRAIN_PER_T    = 1;
    private static final int    INVIS_RECHARGE_PER_T = 1;
    private static final int    INVIS_PULSE_TICKS    = 40;

    private static final float  HITBOX_HEIGHT_SCALE  = 1.25f;

    /* ---------- state ---------- */
    private static final class St {
        boolean invisOn;
        int invisMeter = INVIS_MAX_TICKS;
    }
    private static final Map<UUID, St> ST = new Object2ObjectOpenHashMap<>();
    private static St S(ServerPlayerEntity p) { return ST.computeIfAbsent(p.getUuid(), u -> new St()); }

    /* ---------- meta ---------- */
    @Override public String id() { return "looker"; }
    @Override public boolean hasSlot(String slot) { return "primary".equals(slot) || "secondary".equals(slot); }

    @Override
    public Identifier iconTexture(String slot) {
        return switch (slot) {
            case "primary"   -> new Identifier("odd", "textures/gui/abilities/looker_teleport.png");
            case "secondary" -> new Identifier("odd", "textures/gui/abilities/looker_invis.png");
            default          -> new Identifier("odd", "textures/gui/abilities/ability_default.png");
        };
    }

    @Override public String slotLongDescription(String slot) {
        return switch (slot) {
            case "primary"   -> "Teleport in the direction you are looking.";
            case "secondary" -> "Toggle to turn truly invisible, hiding armour.";
            default          -> "Looker";
        };
    }

    @Override
    public String slotTitle(String slot) {
        return switch (slot) {
            case "primary" -> "DONT BLINK";
            case "secondary" -> "EYES CLOSED";
            default -> Power.super.slotTitle(slot);
        };
    }

    @Override public String longDescription() {
        return "Tal, dark, and... handsome?, you are hard to miss, teleport away from the gazes and sneak behind unsuspecting players.";
    }

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

    @Override
    public void forceDisable(ServerPlayerEntity player) {
        forceStop(player);
    }

    /* =================== IMPORTANT: guaranteed tick hook =================== */

    private static boolean tickRegistered = false;

    public static void ensureTickHook() {
        if (tickRegistered) return;
        tickRegistered = true;

        LookerNet.registerC2S();

        ServerTickEvents.END_SERVER_TICK.register((MinecraftServer server) -> {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                serverTick(p);
            }
        });
    }

    /* ---------- Pehkui: hitbox-only scaling ---------- */
    @Override public void onAssigned(ServerPlayerEntity player) {
        ensureTickHook();
        setHitboxHeight(player, HITBOX_HEIGHT_SCALE);
    }

    public static void onUnassigned(ServerPlayerEntity player) {
        setHitboxHeight(player, 1.0f);
        forceStop(player);
        clearTrackedInvisibility(player);
    }

    private static void setHitboxHeight(ServerPlayerEntity p, float factor) {
        if (p.getWorld().isClient) return;
        ScaleData hbH = ScaleTypes.HITBOX_HEIGHT.getScaleData(p);
        hbH.setTargetScale(factor);
        hbH.setScale(factor);
        p.calculateDimensions();
    }

    public static void installPersistHooks() {
        ensureTickHook();

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity p = handler.player;
            if (hasLooker(p)) setHitboxHeight(p, HITBOX_HEIGHT_SCALE);
            if (!hasLooker(p)) clearTrackedInvisibility(p);
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldP, newP, alive) -> {
            if (hasLooker(newP)) setHitboxHeight(newP, HITBOX_HEIGHT_SCALE);
            clearTrackedInvisibility(newP);
        });
    }

    private static boolean hasLooker(ServerPlayerEntity p) {
        String id = net.seep.odd.abilities.PowerAPI.get(p);
        return Powers.get(id) instanceof LookerPower;
    }

    public static boolean isAbilityInvisible(LivingEntity entity) {
        return entity instanceof OddLookerInvisibility looker && looker.oddities$isLookerInvisible();
    }

    private static void setTrackedInvisibility(ServerPlayerEntity p, boolean on) {
        if (p instanceof OddLookerInvisibility looker) {
            looker.oddities$setLookerInvisible(on);
        }
    }

    private static void clearTrackedInvisibility(ServerPlayerEntity p) {
        setTrackedInvisibility(p, false);
    }

    /* ---------- charges ---------- */
    @Override public boolean usesCharges(String slot) { return "primary".equals(slot); }
    @Override public int maxCharges(String slot)      { return BLINK_MAX_CHARGES; }
    @Override public long rechargeTicks(String slot)  { return BLINK_RECHARGE_T; }

    @Override public long secondaryCooldownTicks()    { return 0; }

    /* ---------- primary: Blink ---------- */
    @Override
    public void activate(ServerPlayerEntity p) {
        if (isPowerless(p)) {
            forceStop(p);
            warnOncePerSec(p, "§cYou are powerless.");
            return;
        }
        blink(p);
    }

    private void blink(ServerPlayerEntity p) {
        World w = p.getWorld();
        Vec3d eye = p.getEyePos();
        Vec3d dir = p.getRotationVector().normalize();
        Vec3d to  = eye.add(dir.multiply(BLINK_MAX_DIST));

        BlockHitResult cast = w.raycast(new RaycastContext(eye, to,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));

        double dist = BLINK_MAX_DIST;
        if (cast.getType() != BlockHitResult.Type.MISS) {
            dist = Math.max(0.5, eye.distanceTo(cast.getPos()) - 0.3);
        }
        Vec3d target = eye.add(dir.multiply(dist));

        Vec3d pos = target;
        for (int i = 0; i < 10; i++) {
            if (w.isSpaceEmpty(p, p.getBoundingBox().offset(pos.subtract(p.getPos())))) break;
            pos = pos.add(dir.multiply(-0.25));
        }

        if (!w.isClient) {
            var sw = (net.minecraft.server.world.ServerWorld) w;
            Vec3d from = p.getPos().add(0, p.getHeight() * 0.5, 0);
            sw.spawnParticles(ParticleTypes.SQUID_INK, from.x, from.y, from.z, 22, 0.25, 0.25, 0.25, 0.06);
        }

        p.teleport(pos.x, pos.y - p.getStandingEyeHeight() + p.getHeight() * 0.5, pos.z);
        w.playSound(null, p.getBlockPos(), ModSounds.LOOKER_INVIS, SoundCategory.PLAYERS, 1.2f, 1f);

        if (!w.isClient) {
            var sw = (net.minecraft.server.world.ServerWorld) w;
            sw.spawnParticles(ParticleTypes.SQUID_INK, pos.x, pos.y, pos.z, 26, 0.28, 0.28, 0.28, 0.07);
        }
    }

    /* ---------- secondary: Invis (meter) ---------- */
    @Override
    public void activateSecondary(ServerPlayerEntity p) {
        if (isPowerless(p)) {
            forceStop(p);
            warnOncePerSec(p, "§cYou are powerless.");
            return;
        }
        netToggleInvis(p);
    }

    public static void netToggleInvis(ServerPlayerEntity p) {
        ensureTickHook();

        if (!hasLooker(p)) return;

        St st = S(p);

        if (!st.invisOn) {
            if (st.invisMeter <= 0) {
                p.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 0.7f);
                LookerNet.sendOverlay(p, false, st.invisMeter, INVIS_MAX_TICKS);
                return;
            }
            setInvisible(p, st, true);
        } else {
            setInvisible(p, st, false);
        }
    }

    private static void setInvisible(ServerPlayerEntity p, St st, boolean on) {
        st.invisOn = on;
        setTrackedInvisibility(p, on);

        if (on) {
            applyShortInvis(p);
            p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.BLOCK_BELL_USE, SoundCategory.PLAYERS, 0.7f, 1.8f);
        } else {
            p.removeStatusEffect(StatusEffects.INVISIBILITY);
            p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.BLOCK_BELL_USE, SoundCategory.PLAYERS, 0.6f, 0.6f);
        }

        LookerNet.sendOverlay(p, on, st.invisMeter, INVIS_MAX_TICKS);
    }

    private static void applyShortInvis(ServerPlayerEntity p) {
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, INVIS_PULSE_TICKS, 0, true, false, false));
    }

    /* ---------- server tick: drain/regen ---------- */
    public static void serverTick(ServerPlayerEntity p) {
        String id = net.seep.odd.abilities.PowerAPI.get(p);
        boolean activeLooker = Powers.get(id) instanceof LookerPower;
        St st = S(p);

        if (!activeLooker) {
            if (st.invisOn) {
                setInvisible(p, st, false);
            } else {
                clearTrackedInvisibility(p);
            }
            return;
        }

        if (isPowerless(p)) {
            if (st.invisOn) setInvisible(p, st, false);
            else clearTrackedInvisibility(p);
            return;
        }

        if (st.invisOn) {
            setTrackedInvisibility(p, true);
            applyShortInvis(p);

            st.invisMeter = Math.max(0, st.invisMeter - INVIS_DRAIN_PER_T);
            if (st.invisMeter == 0) {
                setInvisible(p, st, false);
            } else {
                LookerNet.sendOverlay(p, true, st.invisMeter, INVIS_MAX_TICKS);
            }
        } else {
            clearTrackedInvisibility(p);
            st.invisMeter = Math.min(INVIS_MAX_TICKS, st.invisMeter + INVIS_RECHARGE_PER_T);
        }
    }

    public static void forceStop(ServerPlayerEntity p) {
        ensureTickHook();
        St st = S(p);
        if (st.invisOn) setInvisible(p, st, false);
        else {
            clearTrackedInvisibility(p);
            LookerNet.sendOverlay(p, false, st.invisMeter, INVIS_MAX_TICKS);
        }
    }
}
