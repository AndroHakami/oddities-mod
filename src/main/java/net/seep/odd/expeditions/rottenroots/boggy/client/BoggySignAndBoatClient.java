package net.seep.odd.expeditions.rottenroots.boggy.client;

import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.block.entity.HangingSignBlockEntityRenderer;
import net.minecraft.client.render.block.entity.SignBlockEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.seep.odd.block.ModBlocks;
import net.seep.odd.entity.ModEntities;

public final class BoggySignAndBoatClient {
    private static boolean DONE = false;

    private BoggySignAndBoatClient() {}

    public static void initClient() {
        if (DONE) return;
        DONE = true;

        // Register sign + hanging sign model layers (THIS fixes: "No model for layer odd:sign/boggy#main")
        EntityModelLayerRegistry.registerModelLayer(
                EntityModelLayers.createSign(ModBlocks.BOGGY_WOOD_TYPE),
                SignBlockEntityRenderer::getTexturedModelData
        );

        EntityModelLayerRegistry.registerModelLayer(
                EntityModelLayers.createHangingSign(ModBlocks.BOGGY_WOOD_TYPE),
                HangingSignBlockEntityRenderer::getTexturedModelData
        );

        // Boat renderers (THIS fixes boats using oak textures, if your item spawns these entity types)

    }
}