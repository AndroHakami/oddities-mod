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

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import net.seep.odd.abilities.PowerAPI;
import net.seep.odd.particles.OddParticles;
import org.joml.Vector3f;

import java.util.Map;
import java.util.UUID;

/**
 * Spectral Phase:
 * - No gravity; thrust along look (vertical included).
 * - Entry shove; anti-flight auto-exit (blast obeys phase_toggle).
 * - Charges energy only while grounded/embedded.
 * - Night Vision + Darkness while phased (no particles/icons).
 * - Custom Air meter drains while embedded; suffocates when empty.
 * - HUD shows Energy (red) + Air (blue) side-by-side.
 */
public final class SpectralPhasePower implements Power {

    /* ======================= config ======================= */
    public static final int MAX_CHARGE_TICKS = 20 * 8;   // ~8s to full
    public static final int COOLDOWN_TICKS   = 20 * 6;   // ability system cooldown

    private static final float EXPLOSION_MIN = 1.25f;
    private static final float EXPLOSION_MAX = 4.50f;

    private static final double THRUST_PER_TICK = 0.12;
    private static final double MAX_SPEED       = 0.75;

    private static final int PARTICLE_TRAIL_EVERY = 3;

    private static final DustParticleEffect RED_DUST =
            new DustParticleEffect(new Vector3f(1.0f, 0.08f, 0.08f), 1.0f);

    private static final TagKey<Block> CANNOT_PHASE =
            TagKey.of(RegistryKeys.BLOCK, new Identifier("odd", "cannot_phase"));

    private static final int HUD_SYNC_EVERY = 2;

    private static final int ENTRY_GRACE_TICKS = 8;
    private static final double ENTRY_PUSH_STEP = 0.35;
    private static final int ENTRY_PUSH_STEPS = 2;

    // Status effects (while phased)
    private static final int NV_REFRESH_EVERY   = 40;
    private static final int NV_DURATION_TICKS  = 300;
    private static final int DK_REFRESH_EVERY   = 30;
    private static final int DK_DURATION_TICKS  = 60;

    // Breathing (custom bar)
    private static final int   BREATH_MAX_TICKS      = 20 * 10; // 10s while embedded
    private static final int   BREATH_DRAIN_PER_TICK = 1;       // drain per tick embedded
    private static final int   BREATH_REGEN_PER_TICK = 2;       // regen per tick when not embedded
    private static final int   SUFFOCATE_EVERY       = 10;      // damage cadence when out of air
    private static final float SUFFOCATE_DAMAGE      = 1.0f;    // half-heart

    /* ======================= power meta ======================= */
    @Override public String id() { return "spectral_phase"; }
    @Override public boolean hasSlot(String slot) { return "primary".equals(slot) || "secondary".equals(slot); }
    @Override public long cooldownTicks() { return COOLDOWN_TICKS; }
    @Override public long secondaryCooldownTicks() { return 0; } // phase_toggle has no cooldown

    @Override
    public Identifier iconTexture(String slot) {
        return switch (slot) {
            case "primary"   -> new Identifier("odd", "textures/gui/abilities/spectral_phase.png");
            case "secondary" -> new Identifier("odd", "textures/gui/abilities/phase_toggle.png");
            default          -> new Identifier("odd", "textures/gui/abilities/ability_default.png");
        };
    }
    @Override public String longDescription() {
        return """
            Become semi-transparent and intangible. Thrust along your look direction (vertical included),
            briefly shove into the surface on entry, and auto-exit if you’re fully in open air (blast obeys
            the phase_toggle). Energy only charges while grounded/embedded. While phased you gain Night
            Vision + Darkness. You also have limited air inside blocks; run out and you suffocate.
            """;
    }
    @Override public String slotLongDescription(String slot) {
        return switch (slot) {
            case "primary"   -> "Toggle phasing (thrust, no gravity, energy, air).";
            case "secondary" -> "phase_toggle: Toggle Exit Blast ON/OFF.";
            default          -> "Spectral Phase";
        };
    }

    /* ======================= per-player state ======================= */
    private static final class PhaseData {
        boolean active;
        int charge;
        int entryGrace;
        boolean exitBlastEnabled = true;
        int breath = BREATH_MAX_TICKS;

        // HUD dedupe
        int lastSentCharge = -1;
        int lastSentAirScaled = -1;
        boolean lastSentActive = false;
    }
    private static final Map<UUID, PhaseData> DATA = new Object2ObjectOpenHashMap<>();
    private static PhaseData S(ServerPlayerEntity p) { return DATA.computeIfAbsent(p.getUuid(), u -> new PhaseData()); }

    /* ======================= inputs ======================= */
    @Override
    public void activate(ServerPlayerEntity player) {
        if (!isCurrent(player)) return;
        PhaseData st = S(player);

        if (st.active) {
            if (st.exitBlastEnabled) exitAndBlast(player, st);
            else                     exitNoBlast(player, st);
        } else {
            enterPhase(player, st);
        }
    }

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        if (!isCurrent(player)) return;
        PhaseData st = S(player);
        st.exitBlastEnabled = !st.exitBlastEnabled;
        player.sendMessage(Text.literal("Exit Blast: " + (st.exitBlastEnabled ? "ON" : "OFF")), true);
    }

    public static void handleToggle(ServerPlayerEntity player) {
        new SpectralPhasePower().activate(player);
    }

    /* ======================= server tick ======================= */
    public static void serverTick(ServerPlayerEntity p) {
        if (!isCurrent(p)) return;
        if (!(p.getWorld() instanceof ServerWorld sw)) return;

        PhaseData st = S(p);

        if (st.active) {
            p.noClip = true;
            p.setNoGravity(true);
            p.fallDistance = 0f;

            // Constant effects while phased
            applyPhaseEffects(p);

            // Entry shove (skip air check during grace)
            if (st.entryGrace > 0) {
                Vec3d eye  = p.getEyePos();
                Vec3d look = p.getRotationVector().normalize();
                Vec3d ahead = eye.add(look.multiply(0.7));
                BlockPos aheadPos = BlockPos.ofFloored(ahead);

                if (!sw.isAir(aheadPos) && isPhaseAllowed(sw, aheadPos)) {
                    for (int i = 0; i < ENTRY_PUSH_STEPS; i++) {
                        Vec3d t = p.getPos().add(look.multiply(ENTRY_PUSH_STEP));
                        p.networkHandler.requestTeleport(t.x, t.y, t.z, p.getYaw(), p.getPitch());
                    }
                }
                st.entryGrace--;
            } else {
                // Anti-flight: fully in air → auto-exit (respects phase_toggle)
                BlockPos feet = p.getBlockPos();
                BlockPos head = feet.up();
                if (sw.isAir(feet) && sw.isAir(head)) {
                    exitAuto(p, st);
                    return;
                }
            }

            // Red trail
            if ((p.age % PARTICLE_TRAIL_EVERY) == 0) {
                sw.spawnParticles(RED_DUST, p.getX(), p.getEyeY(), p.getZ(), 3, 0.08, 0.05, 0.08, 0.0);
            }

            // Charge while grounded/embedded
            if (isGroundedWhilePhased(p, sw) && st.charge < MAX_CHARGE_TICKS) st.charge++;

            // Breathing
            boolean embedded = isEmbedded(sw, p);
            if (embedded) {
                st.breath = Math.max(0, st.breath - BREATH_DRAIN_PER_TICK);
                if (st.breath == 0 && (p.age % SUFFOCATE_EVERY) == 0) {
                    p.damage(p.getDamageSources().inWall(), SUFFOCATE_DAMAGE);
                }
            } else {
                st.breath = Math.min(BREATH_MAX_TICKS, st.breath + BREATH_REGEN_PER_TICK);
            }

            // Thrust along look, with cap
            Vec3d look = p.getRotationVector().normalize();
            Vec3d nv = p.getVelocity().add(look.multiply(THRUST_PER_TICK));
            double spd = nv.length();
            if (spd > MAX_SPEED) nv = nv.normalize().multiply(MAX_SPEED);
            p.setVelocity(nv);
            p.velocityModified = true;

            if ((p.age % HUD_SYNC_EVERY) == 0) syncHud(p, st, true);

        } else {
            p.noClip = false;
            p.setNoGravity(false);
            clearPhaseEffects(p);
            if ((p.age % HUD_SYNC_EVERY) == 0) syncHud(p, st, false);
        }
    }

    /* ======================= internals ======================= */
    private static void enterPhase(ServerPlayerEntity p, PhaseData st) {
        st.active = true;
        st.charge = 0;
        st.breath = BREATH_MAX_TICKS;
        st.entryGrace = ENTRY_GRACE_TICKS;

        p.noClip = true;
        p.setVelocity(Vec3d.ZERO);
        applyPhaseEffects(p);

        net.seep.odd.abilities.spectral.SpectralNet.broadcastPhaseState(p, true);

        p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.8f, 1.35f);
        p.sendMessage(Text.literal("Spectral Phase: ON"), true);

        syncHud(p, st, true);
    }

    private static void exitAndBlast(ServerPlayerEntity p, PhaseData st) {
        float ratio = MathHelper.clamp(st.charge / (float) MAX_CHARGE_TICKS, 0f, 1f);

        st.active   = false;
        st.charge   = 0;
        st.entryGrace = 0;
        st.breath = BREATH_MAX_TICKS;

        p.noClip = false;
        clearPhaseEffects(p);

        net.seep.odd.abilities.spectral.SpectralNet.broadcastPhaseState(p, false);
        doScaledExplosion(p, ratio);

        p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 0.7f, 1.15f);
        p.sendMessage(Text.literal("Spectral Phase: OFF (Blast)"), true);

        syncHud(p, st, false);
    }

    private static void exitAuto(ServerPlayerEntity p, PhaseData st) {
        if (st.exitBlastEnabled) {
            p.sendMessage(Text.literal("Spectral Phase: auto-exit (Blast)"), true);
            exitAndBlast(p, st);
        } else {
            p.sendMessage(Text.literal("Spectral Phase: auto-exit"), true);
            exitNoBlast(p, st);
        }
    }

    private static void exitNoBlast(ServerPlayerEntity p, PhaseData st) {
        st.active   = false;
        st.charge   = 0;
        st.entryGrace = 0;
        st.breath = BREATH_MAX_TICKS;

        p.noClip = false;
        clearPhaseEffects(p);

        net.seep.odd.abilities.spectral.SpectralNet.broadcastPhaseState(p, false);

        syncHud(p, st, false);
    }

    /** Restores everything safely (used on disconnect/force-reset). */
    private static void clearState(ServerPlayerEntity p, PhaseData st) {
        st.active = false;
        st.charge = 0;
        st.entryGrace = 0;
        st.breath = BREATH_MAX_TICKS;

        p.noClip = false;
        p.setNoGravity(false);
        p.fallDistance = 0f;
        clearPhaseEffects(p);

        net.seep.odd.abilities.spectral.SpectralNet.broadcastPhaseState(p, false);
        syncHud(p, st, false);
    }

    /** Status effects handling */
    private static void applyPhaseEffects(ServerPlayerEntity p) {
        if ((p.age % NV_REFRESH_EVERY) == 0) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, NV_DURATION_TICKS, 0, true, false, false));
        }
        if ((p.age % DK_REFRESH_EVERY) == 0) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, DK_DURATION_TICKS, 0, true, false, false));
        }
    }
    private static void clearPhaseEffects(ServerPlayerEntity p) {
        p.removeStatusEffect(StatusEffects.NIGHT_VISION);
        p.removeStatusEffect(StatusEffects.DARKNESS);
    }

    /** Explosion plus custom burst particles. */
    private static void doScaledExplosion(ServerPlayerEntity src, float ratio) {
        if (!(src.getWorld() instanceof ServerWorld sw)) return;
        float power = (float) MathHelper.lerp(ratio, EXPLOSION_MIN, EXPLOSION_MAX);

        sw.createExplosion(src, null, null, src.getX(), src.getBodyY(0.5), src.getZ(),
                power, false, net.minecraft.world.World.ExplosionSourceType.NONE);

        sw.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, src.getX(), src.getBodyY(0.5), src.getZ(), 1, 0, 0, 0, 0.0);
        sw.spawnParticles(OddParticles.SPECTRAL_BURST, src.getX(), src.getBodyY(0.5), src.getZ(),
                90, 0.35, 0.35, 0.35, 0.45);
    }

    private static boolean isCurrent(ServerPlayerEntity p) {
        var pow = Powers.get(PowerAPI.get(p));
        return pow instanceof SpectralPhasePower;
    }
    public static boolean isPhased(ServerPlayerEntity p) {
        PhaseData st = DATA.get(p.getUuid());
        return st != null && st.active;
    }
    public static void forceReset(ServerPlayerEntity p) {
        PhaseData st = DATA.get(p.getUuid());
        if (st != null) clearState(p, st);
    }

    /* =========== interaction guards: no attack/use while phased =========== */
    static {
        AttackEntityCallback.EVENT.register((player, w, hand, entity, hit) ->
                (player instanceof ServerPlayerEntity sp && isCurrent(sp) && isPhased(sp)) ? ActionResult.FAIL : ActionResult.PASS);
        UseEntityCallback.EVENT.register((player, w, hand, entity, hit) ->
                (player instanceof ServerPlayerEntity sp && isCurrent(sp) && isPhased(sp)) ? ActionResult.FAIL : ActionResult.PASS);
        AttackBlockCallback.EVENT.register((player, w, hand, pos, dir) ->
                (player instanceof ServerPlayerEntity sp && isCurrent(sp) && isPhased(sp)) ? ActionResult.FAIL : ActionResult.PASS);
        UseBlockCallback.EVENT.register((player, w, hand, hit) ->
                (player instanceof ServerPlayerEntity sp && isCurrent(sp) && isPhased(sp)) ? ActionResult.FAIL : ActionResult.PASS);

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity p = handler.player;
            PhaseData st = DATA.get(p.getUuid());
            if (st != null && st.active) clearState(p, st);
        });
    }

    /* ======================= HUD sync helpers ======================= */
    private static void syncHud(ServerPlayerEntity p, PhaseData st, boolean active) {
        // scale breath to the same range as max energy so the client can reuse "max"
        int airScaled = (int)MathHelper.clamp(
                Math.round((st.breath / (double)BREATH_MAX_TICKS) * MAX_CHARGE_TICKS),
                0, MAX_CHARGE_TICKS
        );

        if (st.lastSentCharge == st.charge && st.lastSentActive == active && st.lastSentAirScaled == airScaled) return;
        st.lastSentCharge = st.charge;
        st.lastSentActive = active;
        st.lastSentAirScaled = airScaled;

        net.seep.odd.abilities.spectral.SpectralNet.sendHud(p, active, st.charge, MAX_CHARGE_TICKS, airScaled);
    }

    /* ======================= CLIENT: Energy + Air HUD (side-by-side) ======================= */
    @Environment(EnvType.CLIENT)
    public static final class Client {
        private static boolean active;
        private static int charge;
        private static int max;
        private static int airScaled; // mapped from 4th int

        public static void onHud(boolean a, int c, int m, int scaledAir) {
            active = a; charge = c; max = m; airScaled = scaledAir;
        }

        public static void init() {
            HudRenderCallback.EVENT.register((DrawContext ctx, float tickDelta) -> {
                if (!active) return;

                MinecraftClient mc = MinecraftClient.getInstance();
                int sw = mc.getWindow().getScaledWidth();
                int sh = mc.getWindow().getScaledHeight();

                int w = 96, h = 8, gap = 8;
                int total = w * 2 + gap;
                int xLeft = (sw - total) / 2;
                int xRight = xLeft + w + gap;
                int y = sh - 56; // a bit higher than HP bar

                // ENERGY (left, red)
                drawBar(ctx, mc, xLeft, y, w, h, charge, max, 0xAAFF0000, "Energy", 0xFFAA3333);

                // AIR (right, blue)
                drawBar(ctx, mc, xRight, y, w, h, airScaled, max, 0xAA66A9FF, "Air", 0xFF66A9FF);
            });
        }

        private static void drawBar(DrawContext ctx, MinecraftClient mc,
                                    int x, int y, int w, int h,
                                    int value, int max, int fillColor,
                                    String label, int labelColor) {
            ctx.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0x66000000); // frame
            ctx.fill(x, y, x + w, y + h, 0x33000000);                 // bg
            float pct = MathHelper.clamp(max == 0 ? 0f : (value / (float) max), 0f, 1f);
            ctx.fill(x, y, x + (int)(w * pct), y + h, fillColor);     // fill
            ctx.drawText(mc.textRenderer, label + " " + (int)(pct * 100) + "%", x, y - 10, labelColor, true);
        }
    }

    /* ======================= helpers ======================= */
    private static boolean isGroundedWhilePhased(ServerPlayerEntity p, ServerWorld sw) {
        BlockPos feet = p.getBlockPos();
        return p.isOnGround() || !sw.isAir(feet) || !sw.isAir(feet.down());
    }
    private static boolean isEmbedded(ServerWorld sw, ServerPlayerEntity p) {
        BlockPos feet = p.getBlockPos();
        BlockPos head = feet.up();
        return !sw.isAir(feet) || !sw.isAir(head);
    }
    private static boolean isPhaseAllowed(ServerWorld sw, BlockPos pos) {
        BlockState s = sw.getBlockState(pos);
        return !s.isIn(CANNOT_PHASE);
    }
}
