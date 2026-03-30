package net.seep.odd.shop;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.device.bank.DabloonBankManager;
import net.seep.odd.item.ModItems;
import net.seep.odd.shop.catalog.ShopCatalogManager;
import net.seep.odd.shop.catalog.ShopEntry;

import java.util.UUID;

public final class ShopNetworking {

    public static final Identifier C2S_BUY = new Identifier(Oddities.MOD_ID, "shop_buy");
    public static final Identifier C2S_PET_NAME = new Identifier(Oddities.MOD_ID, "shop_pet_name");
    public static final Identifier S2C_CATALOG = new Identifier(Oddities.MOD_ID, "shop_catalog");
    public static final Identifier S2C_BALANCE = new Identifier(Oddities.MOD_ID, "shop_balance");
    public static final Identifier S2C_TOAST = new Identifier(Oddities.MOD_ID, "shop_toast");
    public static final Identifier S2C_PURCHASE_RESULT = new Identifier(Oddities.MOD_ID, "shop_purchase_result");

    private static final String PET_TOKEN_KEY = "OddShopPetToken";

    public static void registerC2S() {
        ServerPlayNetworking.registerGlobalReceiver(C2S_BUY, (server, player, handler, buf, responseSender) -> {
            final String entryId = buf.readString(128);
            server.execute(() -> handleBuy((ServerPlayerEntity) player, entryId));
        });

        ServerPlayNetworking.registerGlobalReceiver(C2S_PET_NAME, (server, player, handler, buf, responseSender) -> {
            final String token = buf.readString(128);
            final String name = buf.readString(64);
            server.execute(() -> handlePetName((ServerPlayerEntity) player, token, name));
        });
    }

    public static void registerS2CClient() {
        // implemented client-side in ShopClientNetworking
    }

    public static void sendCatalog(ServerPlayerEntity player) {
        var buf = PacketByteBufs.create();
        buf.writeString(ShopCatalogManager.toJsonForNetwork(), 1_000_000);
        ServerPlayNetworking.send(player, S2C_CATALOG, buf);
    }

    public static void sendBalance(ServerPlayerEntity player) {
        int inventory = countInventoryDabloons(player);
        int bank = DabloonBankManager.getBalance(player.getUuid());
        int total = inventory + bank;

        var buf = PacketByteBufs.create();
        buf.writeInt(inventory);
        buf.writeInt(bank);
        buf.writeInt(total);
        ServerPlayNetworking.send(player, S2C_BALANCE, buf);
    }

    private static void toast(ServerPlayerEntity player, String msg) {
        var buf = PacketByteBufs.create();
        buf.writeString(msg, 512);
        ServerPlayNetworking.send(player, S2C_TOAST, buf);
    }

    private static void sendPurchaseResult(ServerPlayerEntity player, ShopEntry entry, String petToken) {
        var buf = PacketByteBufs.create();
        buf.writeString(entry.id, 128);
        buf.writeString(petToken == null ? "" : petToken, 128);
        ServerPlayNetworking.send(player, S2C_PURCHASE_RESULT, buf);
    }

    private static void handleBuy(ServerPlayerEntity player, String entryId) {
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
        int total = countTotalDabloons(player);
        if (total < price) {
            toast(player, "Not enough dabloons! (" + total + "/" + price + ")");
            sendBalance(player);
            return;
        }

        int removed = removeSpendableDabloons(player, price);
        if (removed < price) {
            toast(player, "Purchase failed (balance desynced).");
            sendBalance(player);
            return;
        }

        String petToken = "";
        boolean granted = false;

        if (entry.grantType == ShopEntry.GrantType.COMMAND) {
            granted = runGrantCommand(player, entry);
        } else {
            ItemStack out = createGrantedStack(entry);
            if (!out.isEmpty()) {
                if (entry.pet) {
                    petToken = UUID.randomUUID().toString();
                    NbtCompound nbt = out.getOrCreateNbt();
                    nbt.putString(PET_TOKEN_KEY, petToken);
                }

                ItemStack remainder = out.copy();
                boolean insertedFully = player.getInventory().insertStack(remainder);
                if (!insertedFully && !remainder.isEmpty()) {
                    player.dropItem(remainder, false);
                }
                player.getInventory().markDirty();
                granted = true;
            }
        }

        if (!granted) {
            toast(player, "Purchase failed (invalid reward).");
            sendBalance(player);
            return;
        }

        toast(player, "Purchased: " + entry.displayName + " (-" + price + ")");
        sendPurchaseResult(player, entry, petToken);
        sendBalance(player);
    }

    private static ItemStack createGrantedStack(ShopEntry entry) {
        Identifier itemId = Identifier.tryParse(entry.giveItemId);
        if (itemId == null || !Registries.ITEM.containsId(itemId)) {
            return ItemStack.EMPTY;
        }

        if (Registries.ITEM.get(itemId) == Items.AIR) {
            return ItemStack.EMPTY;
        }

        return new ItemStack(Registries.ITEM.get(itemId), Math.max(1, entry.giveCount));
    }

    private static boolean runGrantCommand(ServerPlayerEntity player, ShopEntry entry) {
        if (entry.grantCommand == null || entry.grantCommand.isBlank()) {
            return false;
        }

        String command = entry.grantCommand
                .replace("{player}", player.getGameProfile().getName())
                .replace("{uuid}", player.getUuidAsString());

        try {
            int result = player.getServer().getCommandManager().executeWithPrefix(
                    player.getServer().getCommandSource().withLevel(2),
                    command
            );
            return result >= 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void handlePetName(ServerPlayerEntity player, String token, String rawName) {
        String trimmed = rawName == null ? "" : rawName.trim();
        if (token == null || token.isBlank()) return;

        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isEmpty() || !stack.hasNbt()) continue;

            NbtCompound nbt = stack.getNbt();
            if (nbt == null || !token.equals(nbt.getString(PET_TOKEN_KEY))) continue;

            if (!trimmed.isEmpty()) {
                stack.setCustomName(Text.literal(trimmed));
                toast(player, "Pet named: " + trimmed);
            }
            nbt.remove(PET_TOKEN_KEY);
            if (nbt.isEmpty()) {
                stack.setNbt(null);
            }
            player.getInventory().markDirty();
            return;
        }

        toast(player, "Couldn't find that pet egg to rename.");
    }

    public static int countTotalDabloons(ServerPlayerEntity player) {
        return countInventoryDabloons(player) + DabloonBankManager.getBalance(player.getUuid());
    }

    public static int countInventoryDabloons(ServerPlayerEntity player) {
        int count = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (!s.isEmpty() && s.getItem() == ModItems.DABLOON) {
                count += s.getCount();
            }
        }
        return count;
    }

    public static int removeSpendableDabloons(ServerPlayerEntity player, int amount) {
        int remaining = Math.max(0, amount);
        if (remaining <= 0) return 0;

        int removedInventory = removeInventoryDabloons(player, remaining);
        remaining -= removedInventory;

        int removedBank = 0;
        if (remaining > 0) {
            removedBank = DabloonBankManager.takeBalance(player, remaining);
            remaining -= removedBank;
        }

        return removedInventory + removedBank;
    }

    private static int removeInventoryDabloons(ServerPlayerEntity player, int amount) {
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
