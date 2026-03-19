package net.seep.odd.fluid;

import net.minecraft.fluid.FlowableFluid;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;

public final class ModFluids {
    private ModFluids() {}

    public static final FlowableFluid STILL_POISON = Registry.register(
            Registries.FLUID,
            new Identifier(Oddities.MOD_ID, "poison"),
            new PoisonFluid.Still()
    );

    public static final FlowableFluid FLOWING_POISON = Registry.register(
            Registries.FLUID,
            new Identifier(Oddities.MOD_ID, "flowing_poison"),
            new PoisonFluid.Flowing()
    );

    public static void registerModFluids() {
        Oddities.LOGGER.info("Registering Mod Fluids for " + Oddities.MOD_ID);
    }
}