package net.seep.odd.abilities.zerosuit.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.seep.odd.entity.ModEntities;
import net.seep.odd.entity.zerosuit.ZeroBeamRenderer;

/** Call from your client initializer. */
@Environment(EnvType.CLIENT)
public final class ZeroSuitClientInit {
    private ZeroSuitClientInit() {}

    public static void init() {
        EntityRendererRegistry.register(ModEntities.ZERO_BEAM, ZeroBeamRenderer::new);
    }
}
