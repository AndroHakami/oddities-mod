package net.seep.odd.block.rps_machine.client;

import net.seep.odd.block.rps_machine.RpsMachineBlockEntity;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class RpsMachineBlockRenderer extends GeoBlockRenderer<RpsMachineBlockEntity> {
    public RpsMachineBlockRenderer() {
        super(new RpsMachineBlockModel());
    }
}