// FILE: src/main/java/net/seep/odd/abilities/artificer/vialmatrix/VialMatrixInit.java
package net.seep.odd.abilities.artificer.vialmatrix;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;

public final class VialMatrixInit {
    private VialMatrixInit() {}

    public static void init() {
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            if (oldPlayer instanceof VialMatrixHolder o && newPlayer instanceof VialMatrixHolder n) {
                n.odd$setVialMatrixData(o.odd$getVialMatrixData().copy());
            }
        });
    }
}