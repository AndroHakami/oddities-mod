package net.seep.odd.shop.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.seep.odd.shop.ShopNetworking;

public final class ShopClientNetworking {

    public static void registerS2C() {
        ClientPlayNetworking.registerGlobalReceiver(ShopNetworking.S2C_CATALOG, (client, handler, buf, responseSender) -> {
            final String json = buf.readString(1_000_000);
            client.execute(() -> ClientShopState.setCatalogJson(json));
        });

        ClientPlayNetworking.registerGlobalReceiver(ShopNetworking.S2C_BALANCE, (client, handler, buf, responseSender) -> {
            final int bal = buf.readInt();
            client.execute(() -> ClientShopState.setBalance(bal));
        });

        ClientPlayNetworking.registerGlobalReceiver(ShopNetworking.S2C_TOAST, (client, handler, buf, responseSender) -> {
            final String msg = buf.readString(512);
            client.execute(() -> ClientShopState.toast(msg));
        });
    }

    private ShopClientNetworking() {}
}
