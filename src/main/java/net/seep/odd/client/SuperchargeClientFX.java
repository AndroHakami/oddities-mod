// src/main/java/net/seep/odd/client/SuperchargeClientFX.java
package net.seep.odd.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.abilities.power.SuperChargePower;

import java.util.concurrent.ThreadLocalRandom;

/** Subtle WAX_ON particles around hands while holding a supercharged item. */
public final class SuperchargeClientFX {
    private SuperchargeClientFX() {}

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc == null || mc.player == null || mc.world == null) return;
            if ((mc.player.age % 3) != 0) return; // gentle cadence

            spawnForHand(mc, Hand.MAIN_HAND);
            spawnForHand(mc, Hand.OFF_HAND);
        });
    }

    private static void spawnForHand(MinecraftClient mc, Hand hand) {
        PlayerEntity p = mc.player;
        ItemStack s = p.getStackInHand(hand);
        if (!SuperChargePower.isSupercharged(s)) return;

        // Determine if THIS logical hand is the physical right hand
        boolean mainIsRight = (p.getMainArm() == Arm.RIGHT);
        boolean thisIsRight = (hand == Hand.MAIN_HAND) ? mainIsRight : !mainIsRight;

        // Rough hand position near the camera/player
        Vec3d eye  = p.getEyePos().subtract(0, 0.30, 0);
        Vec3d look = p.getRotationVector().normalize();
        Vec3d up   = new Vec3d(0, 1, 0);
        Vec3d right = look.crossProduct(up).normalize();

        double side = thisIsRight ? 0.26 : -0.26;
        Vec3d base = eye.add(look.multiply(0.38)).add(right.multiply(side));

        // Tiny jitter + slight outward drift
        var r = ThreadLocalRandom.current();
        double jx = (r.nextDouble() - 0.5) * 0.05;
        double jy = (r.nextDouble() - 0.5) * 0.05;
        double jz = (r.nextDouble() - 0.5) * 0.05;

        Vec3d vel = look.multiply(0.02).add(right.multiply(side * 0.05));

        mc.world.addParticle(ParticleTypes.WAX_ON,
                base.x + jx, base.y + jy, base.z + jz,
                vel.x, vel.y * 0.3, vel.z);
    }
}
