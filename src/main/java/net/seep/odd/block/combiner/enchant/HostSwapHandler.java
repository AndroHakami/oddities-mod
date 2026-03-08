// src/main/java/net/seep/odd/block/combiner/enchant/HostSwapHandler.java
package net.seep.odd.block.combiner.enchant;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class HostSwapHandler {
    private HostSwapHandler(){}

    private static final Map<UUID, Long> cdUntil = new HashMap<>();

    private static final int COOLDOWN_TICKS = 60;   // 3s
    private static final double RANGE = 1000.0;
    private static final double RANGE_SQ = RANGE * RANGE;

    public static void trySwap(ServerPlayerEntity p) {
        if (p == null || !p.isAlive()) return;
        if (!(p.getWorld() instanceof ServerWorld sw)) return;

        if (CombinerEnchantments.HOST == null) return;

        var helm = p.getEquippedStack(EquipmentSlot.HEAD);
        if (helm == null || helm.isEmpty()) return;

        if (EnchantmentHelper.getLevel(CombinerEnchantments.HOST, helm) <= 0) return;

        long now = sw.getTime();
        long until = cdUntil.getOrDefault(p.getUuid(), 0L);
        if (now < until) return;

        if (p.hasVehicle() || p.isSleeping() || p.isSpectator()) return;

        // find nearest HOST player in same world
        ServerPlayerEntity best = null;
        double bestD2 = Double.MAX_VALUE;

        for (ServerPlayerEntity other : sw.getPlayers()) {
            if (other == p) continue;
            if (!other.isAlive() || other.isSpectator() || other.isSleeping()) continue;
            if (other.hasVehicle()) continue;

            var oh = other.getEquippedStack(EquipmentSlot.HEAD);
            if (oh == null || oh.isEmpty()) continue;
            if (EnchantmentHelper.getLevel(CombinerEnchantments.HOST, oh) <= 0) continue;

            double d2 = other.squaredDistanceTo(p);
            if (d2 > RANGE_SQ) continue;

            if (d2 < bestD2) {
                bestD2 = d2;
                best = other;
            }
        }

        if (best == null) return;

        // swap positions (same world only)
        Vec3d aPos = p.getPos();
        float aYaw = p.getYaw();
        float aPitch = p.getPitch();

        Vec3d bPos = best.getPos();
        float bYaw = best.getYaw();
        float bPitch = best.getPitch();

        // cooldown for both to prevent spam chains
        cdUntil.put(p.getUuid(), now + COOLDOWN_TICKS);
        cdUntil.put(best.getUuid(), now + COOLDOWN_TICKS);

        p.fallDistance = 0;
        best.fallDistance = 0;

        p.teleport(sw, bPos.x, bPos.y, bPos.z, bYaw, bPitch);
        best.teleport(sw, aPos.x, aPos.y, aPos.z, aYaw, aPitch);

        sw.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.8f, 1.2f);
        sw.playSound(null, best.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.8f, 1.2f);
    }
}