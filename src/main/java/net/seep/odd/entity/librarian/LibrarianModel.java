
package net.seep.odd.entity.librarian;

import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public final class LibrarianModel extends GeoModel<LibrarianEntity> {
    @Override
    public Identifier getModelResource(LibrarianEntity entity) {
        return new Identifier("odd", "geo/librarian.geo.json");
    }

    @Override
    public Identifier getTextureResource(LibrarianEntity entity) {
        return new Identifier("odd", "textures/entity/librarian.png");
    }

    @Override
    public Identifier getAnimationResource(LibrarianEntity entity) {
        return new Identifier("odd", "animations/librarian.animation.json");
    }
}
