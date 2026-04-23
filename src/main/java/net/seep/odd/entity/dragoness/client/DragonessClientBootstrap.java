package net.seep.odd.entity.dragoness.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.entity.ModEntities;
import net.seep.odd.entity.dragoness.DragonessRenderer;
import net.seep.odd.entity.dragoness.UfoProtectorEntity;
import net.seep.odd.entity.dragoness.UfoProtectorRenderer;

@Environment(EnvType.CLIENT)
public final class DragonessClientBootstrap {
    private static boolean inited = false;

    private DragonessClientBootstrap() {}

    @SuppressWarnings("unchecked")
    public static void init() {
        if (inited) return;
        inited = true;

        EntityRendererRegistry.register(ModEntities.DRAGONESS, DragonessRenderer::new);
        EntityRendererRegistry.register(ModEntities.UFO_PROTECTOR, UfoProtectorRenderer::new);
        DragonessClientFx.init();
    }
}
