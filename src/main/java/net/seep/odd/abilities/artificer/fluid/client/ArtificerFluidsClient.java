package net.seep.odd.abilities.artificer.fluid.client;

import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;

import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.fabricmc.fabric.api.client.render.fluid.v1.SimpleFluidRenderHandler;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;

import net.seep.odd.abilities.artificer.EssenceType;
import net.seep.odd.abilities.artificer.fluid.ArtificerFluids;

public final class ArtificerFluidsClient {
    private ArtificerFluidsClient() {}

    private static Identifier still(String key) { return new Identifier("odd", "block/" + key + "_still"); }
    private static Identifier flow (String key) { return new Identifier("odd", "block/" + key + "_flow"); }

    public static void registerClient() {
        for (EssenceType t : EssenceType.values()) {
            var still   = ArtificerFluids.still(t);
            var flowing = ArtificerFluids.FLOWING[t.ordinal()];

            // Uses your two textures; color = white (no tint)
            FluidRenderHandlerRegistry.INSTANCE.register(
                    still, flowing,
                    new SimpleFluidRenderHandler(
                            new Identifier("odd", "block/" + t.key + "_still"),
                            new Identifier("odd", "block/" + t.key + "_flow"),
                            0xFFFFFFFF
                    )
            );

            // If you want “truly opaque” rendering, use SOLID. (Translucent also works if textures are opaque.)
            BlockRenderLayerMap.INSTANCE.putFluids(RenderLayer.getSolid(), still, flowing);
        }
    }
}
