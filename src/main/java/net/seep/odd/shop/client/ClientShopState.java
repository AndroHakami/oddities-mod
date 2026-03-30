package net.seep.odd.shop.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.seep.odd.shop.catalog.ShopEntry;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ClientShopState {

    public record PurchaseResult(String entryId, String petToken) {}

    private static final Gson GSON = new Gson();
    private static final Type LIST_TYPE = new TypeToken<List<ShopEntry>>(){}.getType();

    private static List<ShopEntry> catalog = new ArrayList<>();
    private static int inventoryBalance = 0;
    private static int bankBalance = 0;
    private static int totalBalance = 0;
    private static String toast = null;
    private static long toastUntilMs = 0;
    private static PurchaseResult pendingPurchaseResult = null;

    private static final Comparator<ShopEntry> ENTRY_ORDER = Comparator
            .comparing((ShopEntry e) -> e.category == null ? ShopEntry.Category.MISC.ordinal() : e.category.ordinal())
            .thenComparingInt(e -> e.sortOrder)
            .thenComparing(e -> e.displayName == null ? "" : e.displayName.toLowerCase())
            .thenComparing(e -> e.id == null ? "" : e.id.toLowerCase());

    public static List<ShopEntry> catalog() {
        return catalog;
    }

    public static List<ShopEntry> entriesFor(ShopEntry.Category category) {
        List<ShopEntry> out = new ArrayList<>();
        for (ShopEntry entry : catalog) {
            if ((entry.category == null ? ShopEntry.Category.MISC : entry.category) == category) {
                out.add(entry);
            }
        }
        out.sort(ENTRY_ORDER);
        return out;
    }

    public static int inventoryBalance() {
        return inventoryBalance;
    }

    public static int bankBalance() {
        return bankBalance;
    }

    public static int balance() {
        return totalBalance;
    }

    public static void setCatalogJson(String json) {
        try {
            List<ShopEntry> list = GSON.fromJson(json, LIST_TYPE);
            catalog = (list == null) ? new ArrayList<>() : list;
            catalog.sort(ENTRY_ORDER);
        } catch (Exception e) {
            catalog = new ArrayList<>();
        }
    }

    public static void setBalanceBreakdown(int inventory, int bank, int total) {
        inventoryBalance = Math.max(0, inventory);
        bankBalance = Math.max(0, bank);
        totalBalance = Math.max(0, total);
    }

    public static void queuePurchaseResult(String entryId, String petToken) {
        pendingPurchaseResult = new PurchaseResult(entryId, petToken == null ? "" : petToken);
    }

    public static PurchaseResult consumePurchaseResult() {
        PurchaseResult result = pendingPurchaseResult;
        pendingPurchaseResult = null;
        return result;
    }

    public static void toast(String msg) {
        toast = msg;
        toastUntilMs = System.currentTimeMillis() + 1800L;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.inGameHud != null) {
            client.inGameHud.setOverlayMessage(Text.literal(msg), false);
        }
    }

    public static String toastText() {
        if (toast == null) return null;
        if (System.currentTimeMillis() > toastUntilMs) return null;
        return toast;
    }

    private ClientShopState() {}
}
