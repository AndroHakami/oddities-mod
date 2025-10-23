package net.seep.odd.abilities.artificer.condenser;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.fabricmc.fabric.api.client.screenhandler.v1.ScreenRegistry;

@Environment(EnvType.CLIENT)
public final class ArtificerCreateClient {
    private ArtificerCreateClient() {}

    private static boolean REGISTERED_CLIENT = false;

    public static void registerClient() {
        if (REGISTERED_CLIENT) return;

        ScreenRegistry.register(ArtificerCreateInit.CONDENSER_SH, CondenserScreen::new);
        CondenserNet.registerClient();

        REGISTERED_CLIENT = true;
    }
}
