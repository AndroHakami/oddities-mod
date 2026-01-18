package net.seep.odd.particles;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.ParticleType;
import net.minecraft.particle.DefaultParticleType; // use DefaultParticleType if your mappings say so
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;


public final class OddParticles {
    public static final DefaultParticleType SPECTRAL_BURST = FabricParticleTypes.simple();
    public static final DefaultParticleType ICE_FLAKE = FabricParticleTypes.simple();
    public static final DefaultParticleType SPOTTED_STEPS = FabricParticleTypes.simple();
    public static final DefaultParticleType ZERO_GRAVITY = FabricParticleTypes.simple();
    public static final DefaultParticleType TELEKINESIS = FabricParticleTypes.simple();
    public static final DefaultParticleType SPLASH_BUBBLE_GREEN = FabricParticleTypes.simple();
    public static final DefaultParticleType SPLASH_BUBBLE_AQUA  = FabricParticleTypes.simple();
    public static final DefaultParticleType SPLASH_BUBBLE_PINK  = FabricParticleTypes.simple();

    public static void register() {
        Registry.register(Registries.PARTICLE_TYPE, new Identifier("odd", "spectral_burst"), SPECTRAL_BURST);
        Registry.register(Registries.PARTICLE_TYPE, new Identifier("odd", "ice_flake"), ICE_FLAKE);
        Registry.register(Registries.PARTICLE_TYPE, new Identifier("odd", "spotted_steps"), SPOTTED_STEPS);
        Registry.register(Registries.PARTICLE_TYPE, new Identifier("odd", "zero_gravity"), ZERO_GRAVITY);
        Registry.register(Registries.PARTICLE_TYPE, new Identifier("odd", "telekinesis"), TELEKINESIS);
        Registry.register(Registries.PARTICLE_TYPE, new Identifier("odd", "splash_bubble_green"), SPLASH_BUBBLE_GREEN);
        Registry.register(Registries.PARTICLE_TYPE, new Identifier("odd", "splash_bubble_aqua"), SPLASH_BUBBLE_AQUA);
        Registry.register(Registries.PARTICLE_TYPE, new Identifier("odd", "splash_bubble_pink"), SPLASH_BUBBLE_PINK);


    }

    private OddParticles() {}
}