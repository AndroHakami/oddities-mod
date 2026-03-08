// src/main/java/net/seep/odd/block/combiner/enchant/TectonicPistonHandler.java
package net.seep.odd.block.combiner.enchant;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TectonicPistonHandler {
    private TectonicPistonHandler() {}

    private static boolean installed = false;

    // tuneables
    private static final int CHARGE_TICKS = 40;    // 2s
    private static final int COOLDOWN_TICKS = 80;  // 4s
    private static final double MIN_MOVE_SQ = 0.0012;

    // approx “12 blocks” launch feel
    private static final double LAUNCH_Y = 1.25;

    // keep no-fall for a short time after launch
    private static final int NOFALL_TICKS = 100;

    private static final class St {
        int charge;
        long cdUntil;
        long noFallUntil;
    }

    private static final Map<UUID, St> S = new HashMap<>();

    public static void init() {
        if (installed) return;
        installed = true;

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                tick(p);
            }
        });
    }

    private static void tick(ServerPlayerEntity p) {
        if (!(p.getWorld() instanceof ServerWorld sw)) return;

        St st = S.computeIfAbsent(p.getUuid(), k -> new St());
        long now = sw.getTime();

        // maintain no-fall window
        if (now < st.noFallUntil) {
            p.fallDistance = 0.0f;
        }

        var legs = p.getEquippedStack(EquipmentSlot.LEGS);
        if (CombinerEnchantments.RAISER == null ||
                legs == null || legs.isEmpty() ||
                EnchantmentHelper.getLevel(CombinerEnchantments.RAISER, legs) <= 0) {
            st.charge = 0;
            return;
        }

        if (now < st.cdUntil) {
            st.charge = 0;
            return;
        }

        // must be sneaking
        if (!p.isSneaking() || !p.isOnGround()) {
            st.charge = 0;
            return;
        }

        // must not move
        Vec3d v = p.getVelocity();
        double moveSq = v.x * v.x + v.z * v.z;
        if (moveSq > MIN_MOVE_SQ) {
            st.charge = 0;
            return;
        }

        st.charge++;

        // charge particles (subtle)
        if ((st.charge % 6) == 0) {
            BlockPos bp = p.getBlockPos();
            sw.spawnParticles(ParticleTypes.CLOUD,
                    bp.getX() + 0.5, bp.getY() + 0.05, bp.getZ() + 0.5,
                    3,
                    0.25, 0.02, 0.25,
                    0.01);
        }

        if (st.charge < CHARGE_TICKS) return;

        // LAUNCH
        st.charge = 0;
        st.cdUntil = now + COOLDOWN_TICKS;
        st.noFallUntil = now + NOFALL_TICKS;

        p.fallDistance = 0.0f;
        p.addVelocity(0.0, LAUNCH_Y, 0.0);
        p.velocityModified = true;

        sw.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_PISTON_EXTEND, SoundCategory.PLAYERS, 0.9f, 0.9f);
        sw.spawnParticles(ParticleTypes.EXPLOSION, p.getX(), p.getY() + 0.1, p.getZ(), 1, 0, 0, 0, 0);
    }
}