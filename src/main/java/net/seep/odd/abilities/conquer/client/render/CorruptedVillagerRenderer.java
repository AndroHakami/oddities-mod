package net.seep.odd.abilities.conquer.client.render;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.VillagerEntityRenderer;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.abilities.conquer.client.CorruptionRenderUtil;

public final class CorruptedVillagerRenderer extends VillagerEntityRenderer {

    private static final Identifier DARK_VILLAGER =
            new Identifier(Oddities.MOD_ID, "textures/entity/conquer/dark_villager.png");

    private static boolean printedOnce = false;

    public CorruptedVillagerRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        System.out.println("[Oddities][Conquer] CorruptedVillagerRenderer CONSTRUCTED");
    }

    @Override
    public Identifier getTexture(VillagerEntity entity) {
        return DARK_VILLAGER;
    }

}
