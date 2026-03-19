package net.seep.odd.fluid;

import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.fabricmc.fabric.api.client.render.fluid.v1.SimpleFluidRenderHandler;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;

public final class ModFluidRendering {
    private ModFluidRendering() {}

    public static void register() {
        FluidRenderHandlerRegistry.INSTANCE.register(
                ModFluids.STILL_POISON,
                ModFluids.FLOWING_POISON,
                new SimpleFluidRenderHandler(
                        new Identifier(Oddities.MOD_ID, "block/poison_still"),
                        new Identifier(Oddities.MOD_ID, "block/poison_flow"),
                        new Identifier(Oddities.MOD_ID, "block/poison_overlay")
                )
        );

        BlockRenderLayerMap.INSTANCE.putFluids(
                RenderLayer.getTranslucent(),
                ModFluids.STILL_POISON,
                ModFluids.FLOWING_POISON
        );
    }
}