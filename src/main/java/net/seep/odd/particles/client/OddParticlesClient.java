package net.seep.odd.particles.client;

import net.fabricmc.fabric.api.client.particle.v1.FabricSpriteProvider;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.minecraft.client.particle.SpriteProvider;
import net.seep.odd.particles.OddParticles;

public final class OddParticlesClient {
    public static void register() {
        ParticleFactoryRegistry.getInstance().register(
                OddParticles.SPECTRAL_BURST,
                (FabricSpriteProvider sp) -> new SpectralBurstParticle.Factory(sp)
        );
    }
    private OddParticlesClient() {}
}
