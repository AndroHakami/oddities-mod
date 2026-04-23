package net.seep.odd.entity.dragoness.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class DragonessClientFx {
    private static boolean inited = false;

    private DragonessClientFx() {}

    public static void init() {
        if (inited) return;
        inited = true;

        DragonessTabletFx.init();
        DragonessLaserBlockFx.init();
        DragonessLaserBeamFx.init();
        DragonessImpactFx.init();
        DragonessMeteorFx.init();
        DragonessBreakerWaveFx.init();
        DragonessDashPathFx.init();
        DragonessChillBarrierFx.init();
    }
}
