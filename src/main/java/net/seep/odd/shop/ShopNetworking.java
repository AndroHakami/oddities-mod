package net.seep.odd.shop;

import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.item.ModItems;
import net.seep.odd.shop.catalog.ShopCatalogManager;
import net.seep.odd.shop.catalog.ShopEntry;

public final class ShopNetworking {

    public static final Identifier C2S_BUY = new Identifier(Oddities.MOD_ID, "shop_buy");
    public static final Identifier S2C_CATALOG = new Identifier(Oddities.MOD_ID, "shop_catalog");
    public static final Identifier S2C_BALANCE = new Identifier(Oddities.MOD_ID, "shop_balance");
    public static final Identifier S2C_TOAST = new Identifier(Oddities.MOD_ID, "shop_toast");

    public static void registerC2S() {
        ServerPlayNetworking.registerGlobalReceiver(C2S_BUY, (server, player, handler, buf, responseSender) -> {
            final String entryId = buf.readString(128);

            server.execute(() -> handleBuy((ServerPlayerEntity) player, entryId));
        });
    }

    public static void registerS2CClient() {
        // implemented client-side in a separate file (see below)
    }

    public static void sendCatalog(ServerPlayerEntity player) {
        var buf = PacketByteBufs.create();
        buf.writeString(ShopCatalogManager.toJsonForNetwork(), 1_000_000);
        ServerPlayNetworking.send(player, S2C_CATALOG, buf);
    }

    public static void sendBalance(ServerPlayerEntity player) {
        int bal = countDabloons(player);
        var buf = PacketByteBufs.create();
        buf.writeInt(bal);
        ServerPlayNetworking.send(player, S2C_BALANCE, buf);
    }

    private static void toast(ServerPlayerEntity player, String msg) {
        var buf = PacketByteBufs.create();
        buf.writeString(msg, 512);
        ServerPlayNetworking.send(player, S2C_TOAST, buf);
    }

    private static void handleBuy(ServerPlayerEntity player, String entryId) {
        // must have shop UI open
        if (player.currentScreenHandler == null ||
                !player.currentScreenHandler.getClass().getName().endsWith("DabloonsMachineScreenHandler")) {
            return;
        }

        ShopEntry entry = ShopCatalogManager.get(entryId);
        if (entry == null) {
            toast(player, "That item no longer exists.");
            sendBalance(player);
            return;
        }

        int price = Math.max(0, entry.price);
        int bal = countDabloons(player);

        if (bal < price) {
            toast(player, "Not enough dabloons! (" + bal + "/" + price + ")");
            sendBalance(player);
            return;
        }

        // remove dabloons (server-authoritative)
        int removed = removeDabloons(player, price);
        if (removed < price) {
            toast(player, "Purchase failed (balance desynced).");
            sendBalance(player);
            return;
        }

        // grant item
        var item = Registries.ITEM.get(new Identifier(entry.giveItemId));
        ItemStack out = new ItemStack(item, Math.max(1, entry.giveCount));

        boolean inserted = player.getInventory().insertStack(out);
        if (!inserted) {
            player.dropItem(out, false);
        }

        toast(player, "Purchased: " + entry.displayName + " (-" + price + ")");
        sendBalance(player);
    }

    public static int countDabloons(ServerPlayerEntity player) {
        int count = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (!s.isEmpty() && s.getItem() == ModItems.DABLOON) {
                count += s.getCount();
            }
        }
        return count;
    }

    public static int removeDabloons(ServerPlayerEntity player, int amount) {
        int remaining = amount;

        for (int i = 0; i < player.getInventory().size(); i++) {
            if (remaining <= 0) break;

            ItemStack s = player.getInventory().getStack(i);
            if (s.isEmpty() || s.getItem() != ModItems.DABLOON) continue;

            int take = Math.min(remaining, s.getCount());
            s.decrement(take);
            remaining -= take;
        }

        player.getInventory().markDirty();
        return amount - remaining;
    }

    private ShopNetworking() {}
}
