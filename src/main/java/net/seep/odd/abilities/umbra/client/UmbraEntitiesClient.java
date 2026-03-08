package net.seep.odd.abilities.umbra.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

import net.seep.odd.entity.umbra.UmbraEntities;

@Environment(EnvType.CLIENT)
public final class UmbraEntitiesClient {
    private UmbraEntitiesClient() {}

    public static void init() {
        EntityRendererRegistry.register(UmbraEntities.SHADOW_KUNAI, ShadowKunaiRenderer::new);
    }
}