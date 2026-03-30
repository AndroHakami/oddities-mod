package net.seep.odd.shop.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.seep.odd.shop.ShopNetworking;

public final class ShopClientNetworking {

    public static void registerS2C() {
        ClientPlayNetworking.registerGlobalReceiver(ShopNetworking.S2C_CATALOG, (client, handler, buf, responseSender) -> {
            final String json = buf.readString(1_000_000);
            client.execute(() -> ClientShopState.setCatalogJson(json));
        });

        ClientPlayNetworking.registerGlobalReceiver(ShopNetworking.S2C_BALANCE, (client, handler, buf, responseSender) -> {
            final int inventory = buf.readInt();
            final int bank = buf.readInt();
            final int total = buf.readInt();
            client.execute(() -> ClientShopState.setBalanceBreakdown(inventory, bank, total));
        });

        ClientPlayNetworking.registerGlobalReceiver(ShopNetworking.S2C_TOAST, (client, handler, buf, responseSender) -> {
            final String msg = buf.readString(512);
            client.execute(() -> ClientShopState.toast(msg));
        });

        ClientPlayNetworking.registerGlobalReceiver(ShopNetworking.S2C_PURCHASE_RESULT, (client, handler, buf, responseSender) -> {
            final String entryId = buf.readString(128);
            final String petToken = buf.readString(128);
            client.execute(() -> ClientShopState.queuePurchaseResult(entryId, petToken));
        });
    }

    private ShopClientNetworking() {}
}
