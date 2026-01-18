package net.seep.odd.shop.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.seep.odd.shop.catalog.ShopEntry;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public final class ClientShopState {

    private static final Gson GSON = new Gson();
    private static final Type LIST_TYPE = new TypeToken<List<ShopEntry>>(){}.getType();

    private static List<ShopEntry> catalog = new ArrayList<>();
    private static int balance = 0;
    private static String toast = null;
    private static long toastUntilMs = 0;

    public static List<ShopEntry> catalog() { return catalog; }
    public static int balance() { return balance; }

    public static void setCatalogJson(String json) {
        try {
            List<ShopEntry> list = GSON.fromJson(json, LIST_TYPE);
            catalog = (list == null) ? new ArrayList<>() : list;
        } catch (Exception e) {
            catalog = new ArrayList<>();
        }
    }

    public static void setBalance(int b) {
        balance = Math.max(0, b);
    }

    public static void toast(String msg) {
        toast = msg;
        toastUntilMs = System.currentTimeMillis() + 1800;
        MinecraftClient.getInstance().inGameHud.setOverlayMessage(Text.literal(msg), false);
    }

    public static String toastText() {
        if (toast == null) return null;
        if (System.currentTimeMillis() > toastUntilMs) return null;
        return toast;
    }

    private ClientShopState() {}
}
