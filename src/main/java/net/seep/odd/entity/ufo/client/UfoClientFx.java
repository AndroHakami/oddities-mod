package net.seep.odd.entity.ufo.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class UfoClientFx {
    private static boolean inited = false;

    private UfoClientFx() {}

    public static void init() {
        if (inited) return;
        inited = true;

        UfoLoopingAudio.init();
        UfoAbductionBeamFx.init();
        UfoLaserRayFx.init();
        AlienBombLoopingAudio.init();
        AlienBombExplosionFx.init();

        OuterMechLaserFx.init();
        OuterMechWarmupFx.init();
        OuterMechBeamFx.init();
        OuterMechLoopingAudio.init();
        OuterMechBulletFx.init();
    }
}