// src/main/java/net/seep/odd/block/combiner/enchant/DuneStrideHandler.java
package net.seep.odd.block.combiner.enchant;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SandBlock;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.Blocks;

public final class DuneStrideHandler {
    private DuneStrideHandler() {}

    private static boolean installed = false;

    /* =======================
       EASY TUNING KNOBS
       ======================= */

    /** How long the speed is refreshed for (ticks). Higher = feels smoother. */
    private static final int EFFECT_TICKS = 94;

    /** Consider "walking" if horizontal speed^2 >= this. Lower = triggers more easily. */
    private static final double MIN_MOVE_SQ = 0.0005;

    /** Extra speed amplifier bonus. */
    private static final int BONUS_AMP = 5;

    /** Hard cap for amplifier (0=Speed I, 1=Speed II, 2=Speed III...). */
    private static final int MAX_AMP = 5;

    /* =======================
       PARTICLE TUNING
       ======================= */

    /** How often to spawn the trail (ticks). 2 = every 0.1s. */
    private static final int TRAIL_EVERY_TICKS = 2;

    /** Base particle count per spawn. Keep low. */
    private static final int TRAIL_COUNT = 2;

    /** How far behind the player the dust spawns. */
    private static final double TRAIL_BEHIND_DIST = 0.38;

    /** Vertical position offset (near feet). */
    private static final double TRAIL_Y_OFFSET = 0.08;

    /** Spawn spread (random). */
    private static final double TRAIL_SPREAD = 0.10;

    /** Particle motion randomness (speed param in spawnParticles). */
    private static final double TRAIL_MOTION = 0.015;

    public static void init() {
        if (installed) return;
        installed = true;

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                tickPlayer(p);
            }
        });
    }

    private static void tickPlayer(ServerPlayerEntity p) {
        if (!(p.getWorld() instanceof ServerWorld sw)) return;

        var boots = p.getEquippedStack(EquipmentSlot.FEET);
        if (boots == null || boots.isEmpty()) return;

        if (CombinerEnchantments.DUNE == null) return;
        int lvl = EnchantmentHelper.getLevel(CombinerEnchantments.DUNE, boots);
        if (lvl <= 0) return;

        // only while on ground & moving (walking)
        if (!p.isOnGround()) return;

        Vec3d v = p.getVelocity();
        double moveSq = v.x * v.x + v.z * v.z;
        if (moveSq < MIN_MOVE_SQ) return;

        BlockPos below = p.getBlockPos().down();
        BlockState state = sw.getBlockState(below);
        if (!isDesertBlock(state)) return;

        // speed amp: lvl1->0, lvl2->1, ...
        int baseAmp = Math.max(0, lvl - 1);
        int amp = Math.min(MAX_AMP, baseAmp + BONUS_AMP);

        // subtle: ambient=true, showParticles=false, showIcon=false
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, EFFECT_TICKS, amp, true, false, false));

        // sandy dust trail from BEHIND the player
        spawnTrail(sw, p, state);
    }

    private static void spawnTrail(ServerWorld sw, ServerPlayerEntity p, BlockState ground) {
        long time = sw.getTime();
        if ((time % TRAIL_EVERY_TICKS) != 0) return;

        // Determine “behind” based on player facing (not velocity), so it always trails correctly.
        Vec3d look = p.getRotationVec(1.0F);
        Vec3d horiz = new Vec3d(look.x, 0.0, look.z);

        // safety: if somehow zero-length, default to "north-ish"
        if (horiz.lengthSquared() < 1.0E-6) horiz = new Vec3d(0, 0, 1);

        Vec3d behindDir = horiz.normalize().multiply(-1.0);

        Vec3d base = p.getPos().add(
                behindDir.x * TRAIL_BEHIND_DIST,
                TRAIL_Y_OFFSET,
                behindDir.z * TRAIL_BEHIND_DIST
        );

        BlockState dustState = dustStateFor(ground);
        BlockStateParticleEffect dust = new BlockStateParticleEffect(ParticleTypes.FALLING_DUST, dustState);

        // Keep it light. Small spread + tiny motion.
        sw.spawnParticles(
                dust,
                base.x, base.y, base.z,
                TRAIL_COUNT,
                TRAIL_SPREAD, 0.03, TRAIL_SPREAD,
                TRAIL_MOTION
        );
    }

    private static BlockState dustStateFor(BlockState ground) {
        if (ground == null) return Blocks.SAND.getDefaultState();

        // prefer red sand dust when on red-sand family
        if (ground.isOf(Blocks.RED_SAND)
                || ground.isOf(Blocks.RED_SANDSTONE)
                || ground.isOf(Blocks.SMOOTH_RED_SANDSTONE)
                || ground.isOf(Blocks.CUT_RED_SANDSTONE)
                || ground.isOf(Blocks.CHISELED_RED_SANDSTONE)
                || ground.isOf(Blocks.RED_SANDSTONE_STAIRS)
                || ground.isOf(Blocks.RED_SANDSTONE_SLAB)
                || ground.isOf(Blocks.RED_SANDSTONE_WALL)
                || ground.isOf(Blocks.SMOOTH_RED_SANDSTONE_STAIRS)
                || ground.isOf(Blocks.SMOOTH_RED_SANDSTONE_SLAB)
                || ground.isOf(Blocks.CUT_RED_SANDSTONE_SLAB)) {
            return Blocks.RED_SAND.getDefaultState();
        }

        // default sandy dust for everything else in desert family
        return Blocks.SAND.getDefaultState();
    }

    private static boolean isDesertBlock(BlockState s) {
        if (s == null) return false;
        Block b = s.getBlock();

        // sand types (covers sand/red_sand, suspicious sand is its own block)
        if (b instanceof SandBlock) return true;
        if (s.isOf(Blocks.SUSPICIOUS_SAND)) return true;

        // sandstone family + stairs/slabs/walls (vanilla)
        return
                s.isOf(Blocks.SANDSTONE) ||
                        s.isOf(Blocks.CHISELED_SANDSTONE) ||
                        s.isOf(Blocks.CUT_SANDSTONE) ||
                        s.isOf(Blocks.SMOOTH_SANDSTONE) ||
                        s.isOf(Blocks.SANDSTONE_STAIRS) ||
                        s.isOf(Blocks.SANDSTONE_SLAB) ||
                        s.isOf(Blocks.SANDSTONE_WALL) ||
                        s.isOf(Blocks.SMOOTH_SANDSTONE_STAIRS) ||
                        s.isOf(Blocks.SMOOTH_SANDSTONE_SLAB) ||
                        s.isOf(Blocks.CUT_SANDSTONE_SLAB) ||

                        s.isOf(Blocks.RED_SANDSTONE) ||
                        s.isOf(Blocks.CHISELED_RED_SANDSTONE) ||
                        s.isOf(Blocks.CUT_RED_SANDSTONE) ||
                        s.isOf(Blocks.SMOOTH_RED_SANDSTONE) ||
                        s.isOf(Blocks.RED_SANDSTONE_STAIRS) ||
                        s.isOf(Blocks.RED_SANDSTONE_SLAB) ||
                        s.isOf(Blocks.RED_SANDSTONE_WALL) ||
                        s.isOf(Blocks.SMOOTH_RED_SANDSTONE_STAIRS) ||
                        s.isOf(Blocks.SMOOTH_RED_SANDSTONE_SLAB) ||
                        s.isOf(Blocks.CUT_RED_SANDSTONE_SLAB);
    }
}