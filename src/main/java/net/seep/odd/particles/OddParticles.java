package net.seep.odd.particles;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.ParticleType;
import net.minecraft.particle.DefaultParticleType; // use DefaultParticleType if your mappings say so
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;


public final class OddParticles {
    public static final DefaultParticleType SPECTRAL_BURST = FabricParticleTypes.simple();

    public static void register() {
        Registry.register(Registries.PARTICLE_TYPE, new Identifier("odd", "spectral_burst"), SPECTRAL_BURST);
    }

    private OddParticles() {}
}