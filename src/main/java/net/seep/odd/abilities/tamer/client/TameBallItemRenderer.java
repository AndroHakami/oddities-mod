// net/seep/odd/abilities/tamer/client/TameBallItemRenderer.java
package net.seep.odd.abilities.tamer.client;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import net.seep.odd.abilities.tamer.item.TameBallItem;

public class TameBallItemRenderer extends GeoItemRenderer<TameBallItem> {
    public TameBallItemRenderer() { super(new TameBallItemModel()); }


}
