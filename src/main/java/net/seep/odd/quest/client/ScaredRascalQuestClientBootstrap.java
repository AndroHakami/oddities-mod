package net.seep.odd.quest.client;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.seep.odd.entity.ModEntities;

import net.seep.odd.entity.rake.RakeRenderer;
import net.seep.odd.entity.robo_rascal.RoboRascalRenderer;
import net.seep.odd.entity.scared_rascal.ScaredRascalRenderer;
import net.seep.odd.entity.scared_rascal_fight.ScaredRascalFightRenderer;

public final class ScaredRascalQuestClientBootstrap {
    private static boolean inited = false;

    private ScaredRascalQuestClientBootstrap() {
    }

    public static void init() {
        if (inited) return;
        inited = true;

        EntityRendererRegistry.register(ModEntities.SCARED_RASCAL, ScaredRascalRenderer::new);
        EntityRendererRegistry.register(ModEntities.RAKE, RakeRenderer::new);
        EntityRendererRegistry.register(ModEntities.SCARED_RASCAL_FIGHT, ScaredRascalFightRenderer::new);
        EntityRendererRegistry.register(ModEntities.ROBO_RASCAL, RoboRascalRenderer::new);
    }
}
