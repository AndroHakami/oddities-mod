package net.seep.odd.expeditions.rottenroots.boggy.client;

import net.minecraft.client.render.entity.BoatEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;

public final class BoggyBoatRenderer extends BoatEntityRenderer {
    private static final Identifier BOAT_TEX =
            new Identifier(Oddities.MOD_ID, "textures/entity/boat/boggy.png");
    private static final Identifier CHEST_BOAT_TEX =
            new Identifier(Oddities.MOD_ID, "textures/entity/chest_boat/boggy.png");

    private final boolean chest;

    public BoggyBoatRenderer(EntityRendererFactory.Context ctx, boolean chest) {
        super(ctx, chest);
        this.chest = chest;
    }

    @Override
    public Identifier getTexture(BoatEntity entity) {
        return chest ? CHEST_BOAT_TEX : BOAT_TEX;
    }
}