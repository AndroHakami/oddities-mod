package net.seep.odd.entity.booklet.client;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.seep.odd.entity.booklet.BookletEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class BookletRenderer extends GeoEntityRenderer<BookletEntity> {
    public BookletRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new BookletModel());
        this.shadowRadius = 0.25f;
    }
}
