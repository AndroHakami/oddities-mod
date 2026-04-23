package net.seep.odd.device.store.client;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.seep.odd.block.ModBlocks;
import net.seep.odd.block.client.DabloonStoreRenderer;
import net.seep.odd.device.store.DabloonStoreNetworking;
import net.seep.odd.device.store.DabloonStoreSnapshot;
import net.seep.odd.device.store.screen.StoreScreenHandlers;


public final class StoreClientInit {
    private static boolean inited;

    private StoreClientInit() {}

    public static void initClient() {
        if (inited) return;
        inited = true;

        HandledScreens.register(StoreScreenHandlers.DABLOON_STORE, DabloonStoreScreen::new);
        BlockEntityRendererFactories.register(ModBlocks.DABLOON_STORE_BE, DabloonStoreRenderer::new);

        ClientPlayNetworking.registerGlobalReceiver(DabloonStoreNetworking.S2C_BLOCK_SYNC, (client, handler, buf, responseSender) -> {
            var pos = buf.readBlockPos();
            boolean owner = buf.readBoolean();
            var snapshot = DabloonStoreSnapshot.read(buf);

            client.execute(() -> DabloonStoreClientState.putBlockState(
                    new DabloonStoreClientState.BlockStateView(pos, owner, snapshot)
            ));
        });

        ClientPlayNetworking.registerGlobalReceiver(DabloonStoreNetworking.S2C_APP_SYNC, (client, handler, buf, responseSender) -> {
            int discoverableCount = buf.readVarInt();
            List<DabloonStoreSnapshot> discoverable = new ArrayList<>();
            for (int i = 0; i < discoverableCount; i++) {
                discoverable.add(DabloonStoreSnapshot.read(buf));
            }

            int ownedCount = buf.readVarInt();
            List<DabloonStoreSnapshot> owned = new ArrayList<>();
            for (int i = 0; i < ownedCount; i++) {
                owned.add(DabloonStoreSnapshot.read(buf));
            }

            client.execute(() -> DabloonStoreClientState.setAppData(discoverable, owned));
        });
    }
}
