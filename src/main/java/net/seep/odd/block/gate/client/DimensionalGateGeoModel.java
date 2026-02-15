// FILE: src/main/java/net/seep/odd/block/gate/client/DimensionalGateGeoModel.java
package net.seep.odd.block.gate.client;

import net.minecraft.util.Identifier;
import net.seep.odd.block.gate.DimensionalGateBlockEntity;
import net.seep.odd.block.gate.GateStyle;
import net.seep.odd.block.gate.GateStyles;
import software.bernie.geckolib.model.GeoModel;

public class DimensionalGateGeoModel extends GeoModel<DimensionalGateBlockEntity> {

    @Override
    public Identifier getModelResource(DimensionalGateBlockEntity be) {
        GateStyle style = GateStyles.get(be.getStyleId());
        return style.geoModel();
    }

    @Override
    public Identifier getTextureResource(DimensionalGateBlockEntity be) {
        GateStyle style = GateStyles.get(be.getStyleId());
        return style.texture();
    }

    @Override
    public Identifier getAnimationResource(DimensionalGateBlockEntity be) {
        GateStyle style = GateStyles.get(be.getStyleId());
        return style.animation();
    }
}
