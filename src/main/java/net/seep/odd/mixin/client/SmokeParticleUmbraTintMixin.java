// src/main/java/net/seep/odd/mixin/umbra/client/FireSmokeParticleUmbraTintMixin.java
package net.seep.odd.mixin.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.FireSmokeParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.abilities.astral.OddAirSwim;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(FireSmokeParticle.Factory.class)
public abstract class SmokeParticleUmbraTintMixin {

    // dark dark red
    private static final float UMBRA_R = 0.20f;
    private static final float UMBRA_G = 0.02f;
    private static final float UMBRA_B = 0.02f;
    private static final float UMBRA_A = 0.75f;

    @Inject(method = "createParticle", at = @At("RETURN"))
    private void oddities$tintUmbraSmoke(
            DefaultParticleType type,
            ClientWorld world,
            double x, double y, double z,
            double velocityX, double velocityY, double velocityZ,
            CallbackInfoReturnable<Particle> cir
    ) {
        Particle particle = cir.getReturnValue();
        if (particle == null) return;

        // Find a nearby shadow-form player (OddAirSwim true)
        PlayerEntity nearest = null;
        double bestD2 = 3.75 * 3.75;

        for (PlayerEntity pl : world.getPlayers()) {
            if (!(pl instanceof OddAirSwim air) || !air.oddities$isAirSwim()) continue;

            double d2 = pl.squaredDistanceTo(x, y, z);
            if (d2 < bestD2) {
                bestD2 = d2;
                nearest = pl;
            }
        }
        if (nearest == null) return;

        // Only tint if the smoke is clearly moving *toward* the player's center
        Vec3d center = new Vec3d(nearest.getX(), nearest.getBodyY(0.55), nearest.getZ());
        Vec3d pos    = new Vec3d(x, y, z);
        Vec3d toCenter = center.subtract(pos);

        Vec3d v = new Vec3d(velocityX, velocityY, velocityZ);
        double vLen = v.length();
        double tLen = toCenter.length();
        if (vLen < 1.0e-6 || tLen < 1.0e-6) return;

        // angle check so we DON'T recolor torch/campfire smoke near you
        double cos = v.dotProduct(toCenter) / (vLen * tLen);
        if (cos < 0.85) return;

        // Apply dark red + ghostly short lifetime (< 1 second)
        particle.setColor(UMBRA_R, UMBRA_G, UMBRA_B);
        particle.setMaxAge(6 + world.random.nextInt(10)); // 6..15 ticks
    }
}
