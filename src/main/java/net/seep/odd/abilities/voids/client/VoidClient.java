package net.seep.odd.abilities.voids.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.entity.EmptyEntityRenderer;
import net.seep.odd.abilities.voids.VoidRegistry;

@Environment(EnvType.CLIENT)
public final class VoidClient {
    private VoidClient(){}
    public static void init() {
        // portal is “invisible” (particles do the work) – renderer can be empty
        EntityRendererRegistry.register(VoidRegistry.VOID_PORTAL, EmptyEntityRenderer::new);
    }
}
