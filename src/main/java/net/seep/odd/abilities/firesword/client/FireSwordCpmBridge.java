// src/main/java/net/seep/odd/abilities/firesword/client/FireSwordCpmBridge.java
package net.seep.odd.abilities.firesword.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.seep.odd.abilities.overdrive.client.CpmHooks;

@Environment(EnvType.CLIENT)
public final class FireSwordCpmBridge {
    private FireSwordCpmBridge() {}

    public static void conjureStart() {
        FireSwordFx.init();
        FireSwordFx.setActive(true);

        CpmHooks.stop("conjure");
        CpmHooks.play("conjure");
    }

    public static void conjureStop() {
        FireSwordFx.init();
        FireSwordFx.setActive(false);

        CpmHooks.stop("conjure");
    }
}