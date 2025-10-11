package net.seep.odd.abilities.power;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

import net.seep.odd.Oddities;
import net.seep.odd.abilities.PowerAPI;
import net.seep.odd.entity.ModEntities;
import net.seep.odd.entity.spotted.PhantomBuddyEntity;
import net.seep.odd.particles.OddParticles;

import java.util.*;

/**
 * Spotted Phantom
 * - Primary: 12s invis (attack cancels). Random decoy prints while invis (visual only).
 * - Secondary: toggle trail (prints ~every block moved). Each print lingers ~1s with dmg+slow.
 * - Third: summon/recall Phantom Buddy (12x12 storage).
 *
 * NOTE: server tick hookup is done in SpottedNet.initCommon().
 */
public final class SpottedPhantomPower implements Power {

    /* ======================= config ======================= */
    public static final int INVIS_TICKS = 20 * 12; // 12 seconds

    // Decoy tracks while invisible (visual only)
    private static final int   DECOY_EVERY_TICKS = 2;
    private static final float DECOY_CHANCE      = 0.55f;
    private static final float DECOY_RADIUS_MIN  = 0.7f;
    private static final float DECOY_RADIUS_MAX  = 2.2f;

    // Trail spawn: one footprint about every block moved (horizontal distance)
    private static final double STEP_DISTANCE_BLOCKS = 0.9; // 1.0 = exact block cadence

    // Footprint placement offsets (relative to player facing)
    private static final double FOOT_OFFSET      = 0.22; // left/right offset from center
    private static final double FOOT_BACK        = 0.22; // a touch behind player

    // Lingering damage per footprint (~1s each)
    private static final int    TRAIL_LINGER_TICKS       = 20; // 1s
    private static final float  STEP_AOE                 = 0.70f;
    private static final float  STEP_DMG                 = 1.0f; // half heart per window
    private static final int    STEP_SLOW_T              = 20;   // ~1s
    private static final int    STEP_SLOW_A              = 0;    // Slowness I
    private static final int    STEP_HURT_COOLDOWN_TICKS = 4;    // per-target cooldown

    // particle type (client renders ground-aligned; we send zero spread)
    private static final DefaultParticleType STEPS = OddParticles.SPOTTED_STEPS;

    /* ======================= power meta ======================= */
    @Override public String id() { return "spotted_phantom"; }
    @Override public boolean hasSlot(String slot) {
        return "primary".equals(slot) || "secondary".equals(slot) || "third".equals(slot);
    }
    @Override public long cooldownTicks() { return 0; }
    @Override public long secondaryCooldownTicks() { return 0; }
    @Override public long thirdCooldownTicks() { return 0; }

    @Override
    public Identifier iconTexture(String slot) {
        return switch (slot) {
            case "primary"   -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/spotted_invis.png");
            case "secondary" -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/spotted_trail.png");
            case "third"     -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/spotted_buddy.png");
            default          -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/ability_default.png");
        };
    }
    @Override public String longDescription() {
        return """
            Fade from sight and mislead foes with false tracks. Toggle a sprint trail that harms and slows,
            and press the third ability to summon/recall a stash-carrying phantom buddy (12×12 slots).
            """;
    }
    @Override public String slotLongDescription(String slot) {
        return switch (slot) {
            case "primary"   -> "Turn completely invisible for 12s (attacking cancels). Spawns fake glowing steps nearby.";
            case "secondary" -> "Toggle trail ON/OFF (prints ~every block you move; each print briefly lingers and damages).";
            case "third"     -> "Summon / Recall a Phantom Buddy companion with 12×12 storage.";
            default          -> "";
        };
    }
    @Override public Identifier portraitTexture() {
        return new Identifier(Oddities.MOD_ID, "textures/gui/overview/spotted_phantom.png");
    }

    /* ======================= per-player state ======================= */
    private static final class St {
        boolean invisActive;
        int invisLeftTicks;

        boolean trailOn;

        UUID buddyId; // entity uuid if spawned

        // Trail distance bookkeeping
        boolean hasTrailAnchor = false;
        double  trailAnchorX, trailAnchorZ;
        boolean leftNext = true; // alternate foot

        // Lingering damage zones
        final Deque<Spot> spots = new ArrayDeque<>();
    }
    private static final Map<UUID, St> DATA = new Object2ObjectOpenHashMap<>();
    private static St S(ServerPlayerEntity p) { return DATA.computeIfAbsent(p.getUuid(), u -> new St()); }

    /** lingering spot with expiry tick */
    private static final class Spot {
        final Vec3d pos;
        final int untilTick;
        Spot(Vec3d pos, int untilTick) { this.pos = pos; this.untilTick = untilTick; }
    }

    /** Per-target “recently hurt by steps” cooldown. */
    private static final Map<UUID, Integer> STEP_COOLDOWN_UNTIL_TICK = new Object2ObjectOpenHashMap<>();

    /** Exposed so SpottedNet can check. */
    public static boolean isCurrentPower(ServerPlayerEntity p) {
        var pow = Powers.get(PowerAPI.get(p));
        return pow instanceof SpottedPhantomPower;
    }

    /* ======================= inputs ======================= */
    @Override
    public void activate(ServerPlayerEntity p) {
        if (!isCurrentPower(p)) return;
        St st = S(p);

        if (st.invisActive) {
            stopInvis(p, st, false);
            return;
        }
        startInvis(p, st);
    }

    @Override
    public void activateSecondary(ServerPlayerEntity p) {
        if (!isCurrentPower(p)) return;
        St st = S(p);

        st.trailOn = !st.trailOn;
        if (!st.trailOn) st.hasTrailAnchor = false; // reset anchor when turning off

        p.sendMessage(Text.literal("Runner Steps: " + (st.trailOn ? "ON" : "OFF")), true);
        p.getWorld().playSound(null, p.getBlockPos(),
                SoundEvents.BLOCK_FURNACE_FIRE_CRACKLE, SoundCategory.PLAYERS,
                0.6f, st.trailOn ? 1.2f : 0.9f);
    }

    @Override
    public void activateThird(ServerPlayerEntity p) {
        if (!isCurrentPower(p)) return;
        St st = S(p);
        toggleBuddy(p, st);
    }

    /* ======================= server tick (called by SpottedNet) ======================= */
    public static void serverTick(ServerPlayerEntity p) {
        if (!isCurrentPower(p)) return;
        World w = p.getWorld();
        if (!(w instanceof ServerWorld sw)) return;

        St st = S(p);

        // Invis upkeep + decoy prints (visual only) — ZERO SPREAD so they appear exactly where we compute
        if (st.invisActive) {
            st.invisLeftTicks--;
            if (st.invisLeftTicks <= 0) {
                stopInvis(p, st, true);
            } else if ((p.age % DECOY_EVERY_TICKS) == 0 && p.getRandom().nextFloat() < DECOY_CHANCE) {
                double r = MathHelper.lerp(p.getRandom().nextDouble(), DECOY_RADIUS_MIN, DECOY_RADIUS_MAX);
                double a = p.getRandom().nextDouble() * Math.PI * 2;
                double x = p.getX() + Math.cos(a) * r;
                double z = p.getZ() + Math.sin(a) * r;
                double y = p.getY() + 0.02; // feet + tiny lift

                sw.spawnParticles(STEPS, x, y, z, 1, 0, 0, 0, 0.0);
                sw.spawnParticles(p, STEPS, false, x, y, z, 1, 0, 0, 0, 0.0);
            }
        }

        // Trail spawn based on distance moved (~1 block per print), ONLY when not invisible
        if (st.trailOn && !st.invisActive && p.isOnGround()) {
            spawnFootprintsByDistance(sw, p, st);
        } else if (!p.isOnGround() || st.invisActive || !st.trailOn) {
            st.hasTrailAnchor = false; // reset anchor when airborne / invis / disabled
        }

        // Apply lingering damage areas
        processTrailAreas(sw, p, st);
    }

    /* ======================= internals ======================= */
    private static void startInvis(ServerPlayerEntity p, St st) {
        st.invisActive = true;
        st.invisLeftTicks = INVIS_TICKS;

        p.setInvisible(true);
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, INVIS_TICKS, 0, true, false, false));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED,        INVIS_TICKS, 1, true, false, false));

        // NEW: mark as spotted-invisible (client-only rendering gate)
        net.seep.odd.abilities.spotted.SpottedNet.broadcastSpottedInvis(p, true);

        p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_AMBIENT, SoundCategory.PLAYERS, 0.7f, 1.35f);
        p.sendMessage(Text.literal("Spotted Phantom: Invisible"), true);
    }

    private static void stopInvis(ServerPlayerEntity p, St st, boolean natural) {
        st.invisActive = false;
        st.invisLeftTicks = 0;

        p.setInvisible(false);
        p.removeStatusEffect(StatusEffects.INVISIBILITY);
        p.removeStatusEffect(StatusEffects.SPEED);

        // NEW: unmark
        net.seep.odd.abilities.spotted.SpottedNet.broadcastSpottedInvis(p, false);

        p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.6f, natural ? 1.0f : 0.8f);
        p.sendMessage(Text.literal("Spotted Phantom: Visible"), true);
    }


    /** Spawn prints when the player has moved ~1 block horizontally since the last print. */
    private static void spawnFootprintsByDistance(ServerWorld sw, ServerPlayerEntity p, St st) {
        double px = p.getX(), pz = p.getZ();

        if (!st.hasTrailAnchor) {
            st.hasTrailAnchor = true;
            st.trailAnchorX = px; st.trailAnchorZ = pz;
            return;
        }

        double dx = px - st.trailAnchorX;
        double dz = pz - st.trailAnchorZ;
        double dist = Math.sqrt(dx*dx + dz*dz);
        if (dist < STEP_DISTANCE_BLOCKS) return;

        // advance anchor by step distance
        double ratio = STEP_DISTANCE_BLOCKS / dist;
        st.trailAnchorX += dx * ratio;
        st.trailAnchorZ += dz * ratio;

        // one footprint at the anchor
        spawnSingleFootprintAt(sw, p, st, st.trailAnchorX, st.trailAnchorZ, st.leftNext);
        st.leftNext = !st.leftNext;
    }

    /** Spawns one footprint (left/right) and adds a 1s lingering spot. ZERO SPREAD so it won't scatter. */
    private static void spawnSingleFootprintAt(ServerWorld sw, ServerPlayerEntity p, St st,
                                               double baseX, double baseZ, boolean leftFoot) {
        double yaw = Math.toRadians(p.getYaw());
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);

        double backX = -cos * FOOT_BACK;
        double backZ = -sin * FOOT_BACK;

        double sideSign = leftFoot ? 1.0 : -1.0;
        double sideX = -sin * FOOT_OFFSET * sideSign;
        double sideZ =  cos * FOOT_OFFSET * sideSign;

        double y = p.getY() + 0.02; // feet + tiny lift

        double fx = baseX + backX + sideX;
        double fz = baseZ + backZ + sideZ;

        // Visuals — **zero spread** so it doesn't scatter around the feet
        sw.spawnParticles(STEPS, fx, y, fz, 1, 0, 0, 0, 0.0);
        sw.spawnParticles(p, STEPS, false, fx, y, fz, 1, 0, 0, 0, 0.0);

        // Lingering damage spot (~1s)
        int until = p.age + TRAIL_LINGER_TICKS;
        st.spots.addLast(new Spot(new Vec3d(fx, y, fz), until));
        while (st.spots.size() > 256) st.spots.removeFirst();
    }

    /** Process lingering areas for damage + slow with per-target cooldown. */
    private static void processTrailAreas(ServerWorld sw, ServerPlayerEntity src, St st) {
        int now = src.age;
        // prune expired
        while (!st.spots.isEmpty() && st.spots.peekFirst().untilTick <= now) {
            st.spots.removeFirst();
        }
        if (st.spots.isEmpty()) return;

        for (Spot s : st.spots) {
            Vec3d pos = s.pos;
            Box box = new Box(
                    pos.x - STEP_AOE, pos.y - 0.35, pos.z - STEP_AOE,
                    pos.x + STEP_AOE, pos.y + 0.35, pos.z + STEP_AOE
            );
            List<LivingEntity> hits = sw.getEntitiesByClass(LivingEntity.class, box,
                    e -> e.isAlive()
                            && e != src
                            && !(e instanceof net.seep.odd.entity.spotted.PhantomBuddyEntity));

            for (LivingEntity e : hits) {
                UUID id = e.getUuid();
                int until = STEP_COOLDOWN_UNTIL_TICK.getOrDefault(id, 0);
                if (now < until) continue;

                e.damage(src.getDamageSources().playerAttack(src), STEP_DMG);
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, STEP_SLOW_T, STEP_SLOW_A, true, true, true), src);

                STEP_COOLDOWN_UNTIL_TICK.put(id, now + STEP_HURT_COOLDOWN_TICKS);
            }
        }
    }

    private static void toggleBuddy(ServerPlayerEntity p, St st) {
        ServerWorld sw = (ServerWorld) p.getWorld();

        // recall if exists
        if (st.buddyId != null) {
            var ent = sw.getEntity(st.buddyId);
            if (ent instanceof PhantomBuddyEntity pb && pb.isAlive()) {
                pb.recallToOwnerAndDrop(p);
                st.buddyId = null;
                p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_FOX_SNIFF, SoundCategory.PLAYERS, 0.7f, 1.2f);
                p.sendMessage(Text.literal("Phantom Buddy: Recalled"), true);
                return;
            }
            st.buddyId = null;
        }

        // spawn new
        PhantomBuddyEntity buddy = ModEntities.PHANTOM_BUDDY.create(sw);
        if (buddy == null) return;
        buddy.refreshPositionAndAngles(p.getX(), p.getY(), p.getZ(), p.getYaw(), p.getPitch());
        buddy.setOwner(p);
        sw.spawnEntity(buddy);
        st.buddyId = buddy.getUuid();

        p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_FOX_AMBIENT, SoundCategory.PLAYERS, 0.7f, 1.0f);
        p.sendMessage(Text.literal("Phantom Buddy: Summoned"), true);
    }

    /** Called by SpottedNet on disconnect to clear invis cleanly. */
    public static void handleDisconnect(ServerPlayerEntity p) {
        St st = DATA.remove(p.getUuid());
        if (st != null && st.invisActive) {
            p.setInvisible(false);
            p.removeStatusEffect(StatusEffects.INVISIBILITY);
            p.removeStatusEffect(StatusEffects.SPEED);
        }
    }

    /* ======================= interaction guards ======================= */
    static {
        // Attacking cancels invis but still allows the action
        AttackEntityCallback.EVENT.register((player, w, hand, entity, hit) -> {
            if (player instanceof ServerPlayerEntity sp && isCurrentPower(sp)) {
                St st = S(sp);
                if (st.invisActive) stopInvis(sp, st, false);
            }
            return ActionResult.PASS;
        });
        AttackBlockCallback.EVENT.register((player, w, hand, pos, dir) -> {
            if (player instanceof ServerPlayerEntity sp && isCurrentPower(sp)) {
                St st = S(sp);
                if (st.invisActive) stopInvis(sp, st, false);
            }
            return ActionResult.PASS;
        });
    }

    /* ======================= CLIENT: compact HUD (seconds left) ======================= */
    @Environment(EnvType.CLIENT)
    public static final class Client {
        private Client() {}
        private static boolean INIT = false; // prevents double HUD

        public static void init() {
            if (INIT) return;
            INIT = true;

            HudRenderCallback.EVENT.register((DrawContext ctx, float tickDelta) -> {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.player == null) return;

                StatusEffectInstance eff = mc.player.getStatusEffect(StatusEffects.INVISIBILITY);
                if (eff == null) return;

                int secs = Math.max(0, eff.getDuration() / 20);
                if (secs <= 0) return;

                int sw = mc.getWindow().getScaledWidth();
                int sh = mc.getWindow().getScaledHeight();
                String label = "Invisible " + secs + "s";

                int w = mc.textRenderer.getWidth(label) + 10;
                int h = 10;
                int x = (sw - w) / 2;
                int y = sh - 58;

                ctx.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0x66000000);
                ctx.fill(x, y, x + w, y + h, 0x33000000);
                ctx.drawText(mc.textRenderer, label, x + 5, y + 1, 0xFFE6E6E6, true);
            });
        }
    }
}
