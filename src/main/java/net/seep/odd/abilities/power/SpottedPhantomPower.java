package net.seep.odd.abilities.power;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

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

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SpottedPhantomPower implements Power {

    /* ======================= config ======================= */
    public static final int INVIS_TICKS = 20 * 12; // 12 seconds
    private static final int HUD_SYNC_EVERY = 3;

    // Decoy track spam control while invisible
    private static final int DECOY_EVERY_TICKS = 4;
    private static final float DECOY_CHANCE     = 0.40f;
    private static final float DECOY_RADIUS_MIN = 0.8f;
    private static final float DECOY_RADIUS_MAX = 2.6f;

    // Runner steps cadence
    private static final int TRAIL_STEP_EVERY = 3;
    private static final double FOOT_OFFSET   = 0.20;   // left/right foot offset
    private static final float STEP_AOE       = 0.55f;  // who gets clipped by a step
    private static final float STEP_DMG       = 1.0f;   // half-heart
    private static final int  STEP_SLOW_T     = 20;     // 1s
    private static final int  STEP_SLOW_A     = 0;      // Slowness I

    // Double-tap window (secondary) to spawn/recall buddy
    private static final int DOUBLE_TAP_WINDOW = 8;

    // particle ref
    private static final DefaultParticleType STEPS = OddParticles.SPOTTED_STEPS;

    /* ======================= power meta ======================= */
    @Override public String id() { return "spotted_phantom"; }
    @Override public boolean hasSlot(String slot) {
        // Primary = invis, Secondary = toggle runner steps + double-tap to spawn/recall buddy
        return "primary".equals(slot) || "secondary".equals(slot);
    }
    @Override public long cooldownTicks() { return 0; }
    @Override public long secondaryCooldownTicks() { return 0; }

    @Override
    public Identifier iconTexture(String slot) {
        return switch (slot) {
            case "primary"   -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/spotted_invis.png");
            case "secondary" -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/spotted_trail.png");
            default          -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/ability_default.png");
        };
    }
    @Override public String longDescription() {
        return """
            Fade from sight and mislead foes with false tracks. Toggle a sprint trail that harms and slows,
            and double-tap secondary to summon/recall a stash-carrying phantom buddy (12×12 slots).
            """;
    }
    @Override public String slotLongDescription(String slot) {
        return switch (slot) {
            case "primary"   -> "Turn completely invisible for 12s (attacking cancels). Spawns fake glowing steps nearby.";
            case "secondary" -> "Toggle sprint trail ON/OFF. Double-tap to spawn/recall your Phantom Buddy.";
            default          -> "Spotted Phantom";
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
        int lastTrailTick;

        int lastSecondaryTick;
        UUID buddyId; // entity uuid if spawned

        // HUD dedupe
        int lastSentSecs = -1;
        boolean lastSentActive = false;
    }
    private static final Map<UUID, St> DATA = new Object2ObjectOpenHashMap<>();
    private static St S(ServerPlayerEntity p) { return DATA.computeIfAbsent(p.getUuid(), u -> new St()); }

    private static boolean isCurrent(ServerPlayerEntity p) {
        var pow = Powers.get(PowerAPI.get(p));
        return pow instanceof SpottedPhantomPower;
    }

    /* ======================= inputs ======================= */
    @Override
    public void activate(ServerPlayerEntity p) {
        if (!isCurrent(p)) return;
        St st = S(p);

        if (st.invisActive) {
            // pressing again early cancels
            stopInvis(p, st, false);
            return;
        }
        startInvis(p, st);
    }

    @Override
    public void activateSecondary(ServerPlayerEntity p) {
        if (!isCurrent(p)) return;
        St st = S(p);
        int now = p.age;

        if (now - st.lastSecondaryTick <= DOUBLE_TAP_WINDOW) {
            st.lastSecondaryTick = 0;
            // double-tap → spawn/recall buddy
            toggleBuddy(p, st);
            return;
        }
        st.lastSecondaryTick = now;

        st.trailOn = !st.trailOn;
        p.sendMessage(Text.literal("Runner Steps: " + (st.trailOn ? "ON" : "OFF")), true);
        p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.UI_BUTTON_CLICK.value(), SoundCategory.PLAYERS, 0.6f, st.trailOn ? 1.2f : 0.9f);
    }

    /* ======================= server tick ======================= */
    public static void serverTick(ServerPlayerEntity p) {
        if (!isCurrent(p)) return;
        World w = p.getWorld();
        if (!(w instanceof ServerWorld sw)) return;

        St st = S(p);

        // Invis upkeep + decoys
        if (st.invisActive) {
            st.invisLeftTicks--;
            if (st.invisLeftTicks <= 0) {
                stopInvis(p, st, true);
            } else {
                // random decoy steps “around” the player to confuse
                if ((p.age % DECOY_EVERY_TICKS) == 0 && p.getRandom().nextFloat() < DECOY_CHANCE) {
                    double r = MathHelper.lerp(p.getRandom().nextDouble(), DECOY_RADIUS_MIN, DECOY_RADIUS_MAX);
                    double a = p.getRandom().nextDouble() * Math.PI * 2;
                    double sx = p.getX() + Math.cos(a) * r;
                    double sz = p.getZ() + Math.sin(a) * r;
                    double sy = Math.floor(p.getY() - 0.2) + 0.01; // hugs ground
                    sw.spawnParticles(STEPS, sx, sy, sz, 1, 0, 0, 0, 0);
                }

                // HUD (client also reads status effect time, but we send a simple boolean for the overlay gating)
                if ((p.age % HUD_SYNC_EVERY) == 0) {
                    int secs = Math.max(0, st.invisLeftTicks / 20);
                    if (secs != st.lastSentSecs || !st.lastSentActive) {
                        st.lastSentSecs = secs; st.lastSentActive = true;
                        // No dedicated net channel — client HUD derives seconds from Invisibility effect.
                        // We still “ping” via action bar so the player feels it’s active.
                        p.sendMessage(Text.literal("Invisible: " + secs + "s"), true);
                    }
                }
            }
        } else {
            if ((p.age % HUD_SYNC_EVERY) == 0 && st.lastSentActive) {
                st.lastSentActive = false;
                p.sendMessage(Text.literal("Invisible: OFF"), true);
            }
        }

        // Runner steps (accurate behind-feet) with tiny DoT + slow
        if (st.trailOn && p.isOnGround() && p.isSprinting()) {
            if ((p.age % TRAIL_STEP_EVERY) == 0) {
                st.lastTrailTick = p.age;
                spawnFootStepsAndAffect(sw, p);
            }
        }
    }

    /* ======================= internals ======================= */
    private static void startInvis(ServerPlayerEntity p, St st) {
        st.invisActive = true;
        st.invisLeftTicks = INVIS_TICKS;

        p.setInvisible(true); // hides armor/hand items too
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, INVIS_TICKS, 0, true, false, false));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED,        INVIS_TICKS, 1, true, false, false));

        p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_AMBIENT, SoundCategory.PLAYERS, 0.7f, 1.35f);
        p.sendMessage(Text.literal("Spotted Phantom: Invisible"), true);
    }

    private static void stopInvis(ServerPlayerEntity p, St st, boolean natural) {
        st.invisActive = false;
        st.invisLeftTicks = 0;

        p.setInvisible(false);
        p.removeStatusEffect(StatusEffects.INVISIBILITY);
        p.removeStatusEffect(StatusEffects.SPEED);

        p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.6f, natural ? 1.0f : 0.8f);
        p.sendMessage(Text.literal("Spotted Phantom: Visible"), true);
    }

    private static void spawnFootStepsAndAffect(ServerWorld sw, ServerPlayerEntity p) {
        // place two prints (L/R) just behind current motion
        float yawRad = (float) Math.toRadians(p.getYaw());
        // side vector
        double sx = Math.cos(yawRad) * FOOT_OFFSET;
        double sz = Math.sin(yawRad) * FOOT_OFFSET;

        double baseY = Math.floor(p.getY() - 0.2) + 0.01;
        Vec3d left  = new Vec3d(p.getX() - sx, baseY, p.getZ() + sz);
        Vec3d right = new Vec3d(p.getX() + sx, baseY, p.getZ() - sz);

        sw.spawnParticles(STEPS, left.x, left.y, left.z, 1, 0, 0, 0, 0);
        sw.spawnParticles(STEPS, right.x, right.y, right.z, 1, 0, 0, 0, 0);

        // Light damage + slow to enemies very close to each fresh print
        affectAt(sw, p, left);
        affectAt(sw, p, right);
    }

    private static void affectAt(ServerWorld sw, ServerPlayerEntity src, Vec3d pos) {
        Box box = new Box(
                pos.x - STEP_AOE, pos.y - 0.25, pos.z - STEP_AOE,
                pos.x + STEP_AOE, pos.y + 0.25, pos.z + STEP_AOE
        );
        List<LivingEntity> hits = sw.getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive() && e != src);
        for (LivingEntity e : hits) {
            e.damage(src.getDamageSources().playerAttack(src), STEP_DMG);
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, STEP_SLOW_T, STEP_SLOW_A, true, true, true), src);
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

    /* ======================= interaction guards: attacks cancel invis ======================= */
    static {
        AttackEntityCallback.EVENT.register((player, w, hand, entity, hit) -> {
            if (player instanceof ServerPlayerEntity sp && isCurrent(sp)) {
                St st = S(sp);
                if (st.invisActive) stopInvis(sp, st, false); // cancel then allow the attack
            }
            return ActionResult.PASS;
        });
        AttackBlockCallback.EVENT.register((player, w, hand, pos, dir) -> {
            if (player instanceof ServerPlayerEntity sp && isCurrent(sp)) {
                St st = S(sp);
                if (st.invisActive) stopInvis(sp, st, false);
            }
            return ActionResult.PASS;
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity p = handler.player;
            St st = DATA.remove(p.getUuid());
            if (st != null && st.invisActive) {
                // hard clear
                p.setInvisible(false);
                p.removeStatusEffect(StatusEffects.INVISIBILITY);
                p.removeStatusEffect(StatusEffects.SPEED);
            }
        });
    }

    /* ======================= CLIENT: compact HUD (seconds left) ======================= */
    @Environment(EnvType.CLIENT)
    public static final class Client {
        private Client() {}

        public static void init() {
            HudRenderCallback.EVENT.register((DrawContext ctx, float tickDelta) -> {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.player == null) return;

                // Derive from vanilla Invisibility remaining time to avoid extra net plumbing.
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
