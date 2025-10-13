package net.seep.odd.abilities.power;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.seep.odd.Oddities;
import virtuoel.pehkui.api.ScaleData;
import virtuoel.pehkui.api.ScaleTypes;

import java.util.Map;
import java.util.UUID;

/** The Looker: short blink (charges), brief invis/phase, and 2.5-block height (Pehkui). */
public final class LookerPower implements Power, ChargedPower, HoldReleasePower {

    // ---- config ----
    private static final double BLINK_MAX_DIST = 5.0;
    private static final int    BLINK_MAX_CHARGES = 3;
    private static final int    BLINK_RECHARGE_T  = 20 * 6;   // 6s per charge

    private static final int    PHASE_DURATION_T  = 20 * 5;   // 5s invisible/untouchable
    private static final int    PHASE_COOLDOWN_T  = 20 * 20;  // 20s cooldown
    private static final float  PHASE_SPEED_PENALTY = 0.60f;

    private static final float  TARGET_HEIGHT_BLOCKS = 2.5f;   // Pehkui
    private static final float  VANILLA_HEIGHT = 1.8f;

    // ---- state ----
    private static final class St {
        int phaseUntilTick; // server tick when phase ends; 0 = not phased
    }
    private static final Map<UUID, St> ST = new Object2ObjectOpenHashMap<>();
    private static St S(ServerPlayerEntity p) { return ST.computeIfAbsent(p.getUuid(), u -> new St()); }

    @Override public String id() { return "looker"; }
    @Override public boolean hasSlot(String slot) { return "primary".equals(slot) || "secondary".equals(slot); }
    @Override public Identifier iconTexture(String slot) {
        return new Identifier(Oddities.MOD_ID, "textures/gui/abilities/" + ("primary".equals(slot) ? "looker_blink.png" : "looker_phase.png"));
    }
    @Override public String longDescription() { return "Blink in the direction you’re looking, and briefly vanish from reality."; }
    @Override public String slotLongDescription(String slot) {
        return "primary".equals(slot)
                ? "Blink: short teleport (5 blocks). 3 charges; each recharges after 6s."
                : "Phase: become invisible and untouchable for 5s (20s cooldown).";
    }

    /* ---------------- Pehkui: set 2.5-block height while assigned ---------------- */
    @Override public void onAssigned(ServerPlayerEntity player) {
        applyScale(player, TARGET_HEIGHT_BLOCKS / VANILLA_HEIGHT);
    }
    /** If you call PowerAPI.clear(...), call this again to reset scale (optional helper). */
    public static void onUnassigned(ServerPlayerEntity player) {
        applyScale(player, 1.0f); // back to normal
    }
    private static void applyScale(ServerPlayerEntity p, float factor) {
        World w = p.getWorld();
        if (w.isClient()) return;
        // Scale height and width proportionally to keep hitbox sane
        ScaleData h = ScaleTypes.HEIGHT.getScaleData(p);
        ScaleData hw = ScaleTypes.HITBOX_HEIGHT.getScaleData(p);
        ScaleData wdt = ScaleTypes.WIDTH.getScaleData(p);
        ScaleData hbw = ScaleTypes.HITBOX_WIDTH.getScaleData(p);

        h.setTargetScale(factor);
        hw.setTargetScale(factor);
        wdt.setTargetScale(factor);
        hbw.setTargetScale(factor);
        // instant for now
        h.setScale(factor); hw.setScale(factor); wdt.setScale(factor); hbw.setScale(factor);
        p.calculateDimensions();
    }

    /* ---------------- charges (primary) ---------------- */
    @Override public boolean usesCharges(String slot) { return "primary".equals(slot); }
    @Override public int maxCharges(String slot)      { return BLINK_MAX_CHARGES; }
    @Override public long rechargeTicks(String slot)  { return BLINK_RECHARGE_T; }

    /* ---------------- cooldown (secondary) ---------------- */
    @Override public long secondaryCooldownTicks()    { return PHASE_COOLDOWN_T; }

    /* ---------------- primary: Blink ---------------- */
    @Override
    public void activate(ServerPlayerEntity p) {
        // Charge gating handled by PowerAPI before calling us.
        blink(p);
    }

    private void blink(ServerPlayerEntity p) {
        World w = p.getWorld();
        Vec3d eye = p.getEyePos();
        Vec3d dir = p.getRotationVector().normalize();
        Vec3d to  = eye.add(dir.multiply(BLINK_MAX_DIST));

        BlockHitResult cast = w.raycast(new RaycastContext(
                eye, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
        double dist = BLINK_MAX_DIST;
        if (cast.getType() != BlockHitResult.Type.MISS) {
            dist = eye.distanceTo(cast.getPos()) - 0.3; // stop a bit short of the block
            if (dist < 0.5) dist = 0.5;
        }
        Vec3d target = eye.add(dir.multiply(dist));

        // find a safe spot: step back in 0.25 increments until collision-free
        Vec3d pos = target;
        for (int i = 0; i < 10; i++) {
            if (w.isSpaceEmpty(p, p.getBoundingBox().offset(pos.subtract(p.getPos())))) break;
            pos = pos.add(dir.multiply(-0.25));
        }

        p.teleport(pos.x, pos.y - p.getStandingEyeHeight() + p.getHeight() * 0.5, pos.z); // keep feet sensible
        w.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.9f, 1.25f);
        // little poof
        ((net.minecraft.server.world.ServerWorld) w).spawnParticles(net.minecraft.particle.ParticleTypes.PORTAL,
                pos.x, pos.y, pos.z, 16, 0.2, 0.2, 0.2, 0.1);
    }

    /* ---------------- secondary: Phase ---------------- */
    @Override
    public void activateSecondary(ServerPlayerEntity p) {
        World w = p.getWorld();
        St st = S(p);
        st.phaseUntilTick = p.age + PHASE_DURATION_T;

        // invis + slow; also invulnerable while phased
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, PHASE_DURATION_T, 0, true, false, false));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, PHASE_DURATION_T, 0, true, false, false));
        p.setInvulnerable(true);

        w.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_BELL_USE, SoundCategory.PLAYERS, 0.7f, 1.8f);
    }

    /* ---------------- tick to end phase safely ---------------- */
    public static void serverTick(ServerPlayerEntity p) {
        String id = net.seep.odd.abilities.PowerAPI.get(p);
        if (!(Powers.get(id) instanceof LookerPower)) return;
        St st = S(p);

        if (st.phaseUntilTick > 0 && p.age >= st.phaseUntilTick) {
            st.phaseUntilTick = 0;
            p.setInvulnerable(false);
        }
    }

    /* ---------------- block combat while phased ---------------- */
    static {
        AttackEntityCallback.EVENT.register((player, world, hand, target, hit) -> {
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            String id = net.seep.odd.abilities.PowerAPI.get(sp);
            if (!(Powers.get(id) instanceof LookerPower)) return ActionResult.PASS;
            if (S(sp).phaseUntilTick > 0) return ActionResult.FAIL; // cannot attack while phased
            return ActionResult.PASS;
        });
        AttackBlockCallback.EVENT.register((player, world, hand, pos, dir) -> {
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            String id = net.seep.odd.abilities.PowerAPI.get(sp);
            if (!(Powers.get(id) instanceof LookerPower)) return ActionResult.PASS;
            if (S(sp).phaseUntilTick > 0) return ActionResult.FAIL; // cannot mine while phased
            return ActionResult.PASS;
        });
    }
}
