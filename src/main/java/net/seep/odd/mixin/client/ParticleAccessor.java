// src/main/java/net/seep/odd/mixin/umbra/client/ParticleAccessor.java
package net.seep.odd.mixin.client;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Particle.class)
public interface ParticleAccessor {
    @Accessor("maxAge") void oddities$setMaxAge(int value);

    @Accessor("red")   void oddities$setRed(float value);
    @Accessor("green") void oddities$setGreen(float value);
    @Accessor("blue")  void oddities$setBlue(float value);
    @Accessor("alpha") void oddities$setAlpha(float value);
}
