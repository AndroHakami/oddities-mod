package net.seep.odd.particles.client;

import net.fabricmc.fabric.api.client.particle.v1.FabricSpriteProvider;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DefaultParticleType;
import net.seep.odd.particles.OddParticles;
import org.jetbrains.annotations.Nullable;

public final class OddParticlesClient {
    public static void register() {
        ParticleFactoryRegistry.getInstance().register(
                OddParticles.SPECTRAL_BURST,
                (FabricSpriteProvider sp) -> new SpectralBurstParticle.Factory(sp)
        );
        ParticleFactoryRegistry.getInstance().register(
                OddParticles.ICE_FLAKE,
                (FabricSpriteProvider sp) -> new IceFlakeParticle.Factory(sp)
        );
        ParticleFactoryRegistry.getInstance().register(
                OddParticles.SPOTTED_STEPS,
                (FabricSpriteProvider sp) -> new SpottedStepsParticle.Factory(sp)
        );
        ParticleFactoryRegistry.getInstance().register(
                OddParticles.ZERO_GRAVITY,
                (FabricSpriteProvider sp) -> new ZeroGravityParticle.Factory(sp)
        );
        ParticleFactoryRegistry.getInstance().register(
                OddParticles.TELEKINESIS,
                (FabricSpriteProvider sp) -> new TelekinesisParticle.Factory(sp)
        );
        ParticleFactoryRegistry.getInstance().register(OddParticles.SPLASH_BUBBLE_GREEN, GreenBubbleParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(OddParticles.SPLASH_BUBBLE_AQUA,  AquaBubbleParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(OddParticles.SPLASH_BUBBLE_PINK,  PinkBubbleParticle.Factory::new);






    }
    private OddParticlesClient() {}
}
