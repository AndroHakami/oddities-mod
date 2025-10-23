package net.seep.odd.abilities.power;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.seep.odd.abilities.looker.LookerNet;
import virtuoel.pehkui.api.ScaleData;
import virtuoel.pehkui.api.ScaleTypes;

import java.util.Map;
import java.util.UUID;

public final class LookerPower implements Power, ChargedPower, HoldReleasePower {

    /* ---------- config ---------- */
    private static final double BLINK_MAX_DIST       = 5.0;

    private static final int    BLINK_MAX_CHARGES    = 3;
    private static final int    BLINK_RECHARGE_T     = 20 * 6;

    private static final int    INVIS_MAX_TICKS      = 20 * 8; // 8s full
    private static final int    INVIS_DRAIN_PER_T    = 1;      // drain per tick
    private static final int    INVIS_RECHARGE_PER_T = 1;      // regen per tick
    private static final int    INVIS_PULSE_TICKS    = 12;     // short renew pulses (~0.6s)

    private static final float  HITBOX_HEIGHT_SCALE  = 1.25f;  // +25% hitbox height (visual unchanged)

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

    // DO NOT TOUCH these (per your request)
    @Override
    public Identifier iconTexture(String slot) {
        return switch (slot) {
            case "primary"   -> new Identifier("odd", "textures/gui/abilities/looker_teleport.png");
            case "secondary" -> new Identifier("odd", "textures/gui/abilities/looker_invis.png");
            default          -> new Identifier("odd", "textures/gui/abilities/ability_default.png");
        };
    }

    @Override public String longDescription() {
        return "Blink a short distance (charges). Toggle invisibility on a draining meter. Hitbox height +25% (visual height unchanged).";
    }
    @Override public String slotLongDescription(String slot) {
        return "primary".equals(slot)
                ? "Blink: 5 blocks. 3 charges, 6s recharge each."
                : "Invisibility: toggle ON/OFF; drains meter and recharges when off.";
    }

    /* ---------- Pehkui: hitbox-only scaling ---------- */
    @Override public void onAssigned(ServerPlayerEntity player) { setHitboxHeight(player, HITBOX_HEIGHT_SCALE); }

    public static void onUnassigned(ServerPlayerEntity player) {
        setHitboxHeight(player, 1.0f);
        St st = ST.get(player.getUuid());
        if (st != null && st.invisOn) setInvisible(player, st, false);
    }

    private static void setHitboxHeight(ServerPlayerEntity p, float factor) {
        if (p.getWorld().isClient()) return;
        // Only hitbox height — width/visual height untouched
        ScaleData hbH = ScaleTypes.HITBOX_HEIGHT.getScaleData(p);
        hbH.setTargetScale(factor);
        hbH.setScale(factor);
        p.calculateDimensions(); // update bounding box immediately
    }

    /** Call once during common init (see snippet below). */
    public static void installPersistHooks() {
        // Re-apply hitbox scale on player join if Looker is selected
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity p = handler.player;
            if (hasLooker(p)) setHitboxHeight(p, HITBOX_HEIGHT_SCALE);
        });
        // Re-apply after respawn/dimension change
        ServerPlayerEvents.AFTER_RESPAWN.register((oldP, newP, alive) -> {
            if (hasLooker(newP)) setHitboxHeight(newP, HITBOX_HEIGHT_SCALE);
        });
    }

    private static boolean hasLooker(ServerPlayerEntity p) {
        String id = net.seep.odd.abilities.PowerAPI.get(p);
        return Powers.get(id) instanceof LookerPower;
    }

    /* ---------- charges ---------- */
    @Override public boolean usesCharges(String slot) { return "primary".equals(slot); }
    @Override public int maxCharges(String slot)      { return BLINK_MAX_CHARGES; }
    @Override public long rechargeTicks(String slot)  { return BLINK_RECHARGE_T; }

    /* ---------- cooldown ---------- */
    @Override public long secondaryCooldownTicks()    { return 0; } // meter-based

    /* ---------- primary: Blink ---------- */
    @Override
    public void activate(ServerPlayerEntity p) { blink(p); }

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

        // step back until free
        Vec3d pos = target;
        for (int i = 0; i < 10; i++) {
            if (w.isSpaceEmpty(p, p.getBoundingBox().offset(pos.subtract(p.getPos())))) break;
            pos = pos.add(dir.multiply(-0.25));
        }

        p.teleport(pos.x, pos.y - p.getStandingEyeHeight() + p.getHeight() * 0.5, pos.z);
        w.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.9f, 1.25f);

        if (!w.isClient()) {
            ((net.minecraft.server.world.ServerWorld) w).spawnParticles(
                    net.minecraft.particle.ParticleTypes.PORTAL, pos.x, pos.y, pos.z, 16, 0.2, 0.2, 0.2, 0.1);
        }
    }

    /* ---------- secondary: Invis (meter) ---------- */
    @Override
    public void activateSecondary(ServerPlayerEntity p) {
        St st = S(p);
        if (!st.invisOn) {
            if (st.invisMeter <= 0) {
                p.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 0.7f);
                return;
            }
            setInvisible(p, st, true);
        } else {
            setInvisible(p, st, false);
        }
    }

    private static void setInvisible(ServerPlayerEntity p, St st, boolean on) {
        st.invisOn = on;
        if (on) {
            applyShortInvis(p); // short pulse, continuously refreshed while active
            p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.BLOCK_BELL_USE, SoundCategory.PLAYERS, 0.7f, 1.8f);
        } else {
            p.removeStatusEffect(StatusEffects.INVISIBILITY); // stop immediately
            p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.BLOCK_BELL_USE, SoundCategory.PLAYERS, 0.6f, 0.6f);
        }
        LookerNet.sendOverlay(p, on, st.invisMeter, INVIS_MAX_TICKS);
    }

    private static void applyShortInvis(ServerPlayerEntity p) {
        // Ambient, no particles, no icon; expires quickly if we stop refreshing.
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, INVIS_PULSE_TICKS, 0, true, false, false));
    }

    /* ---------- server tick: drain/regen ---------- */
    public static void serverTick(ServerPlayerEntity p) {
        // Only tick while Looker is the active power
        String id = net.seep.odd.abilities.PowerAPI.get(p);
        if (!(Powers.get(id) instanceof LookerPower)) return;

        St st = S(p);

        if (st.invisOn) {
            // keep effect alive with pulses; if power changes, invis naturally expires
            applyShortInvis(p);

            st.invisMeter = Math.max(0, st.invisMeter - INVIS_DRAIN_PER_T);
            if (st.invisMeter == 0) {
                setInvisible(p, st, false); // auto off
            } else {
                // keep HUD meter snappy
                LookerNet.sendOverlay(p, true, st.invisMeter, INVIS_MAX_TICKS);
            }
        } else {
            st.invisMeter = Math.min(INVIS_MAX_TICKS, st.invisMeter + INVIS_RECHARGE_PER_T);
        }
    }

    /* ---------- helper for power swaps ---------- */
    public static void forceStop(ServerPlayerEntity p) {
        St st = S(p);
        if (st.invisOn) setInvisible(p, st, false);
    }
}
