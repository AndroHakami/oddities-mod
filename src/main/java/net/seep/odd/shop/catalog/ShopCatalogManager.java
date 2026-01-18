package net.seep.odd.shop.catalog;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.seep.odd.Oddities;
import net.seep.odd.shop.ShopNetworking;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class ShopCatalogManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<ShopEntry>>(){}.getType();

    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve(Oddities.MOD_ID)
            .resolve("dabloons_shop.json");

    private static final Map<String, ShopEntry> ENTRIES = new LinkedHashMap<>();

    public static void init() {
        ensureExists();
        reload();
    }

    public static synchronized void reload() {
        try {
            String json = Files.readString(FILE, StandardCharsets.UTF_8);
            List<ShopEntry> list = GSON.fromJson(json, LIST_TYPE);
            ENTRIES.clear();
            if (list != null) {
                for (ShopEntry e : list) {
                    if (e == null || e.id == null || e.id.isBlank()) continue;
                    ENTRIES.put(e.id, e);
                }
            }
        } catch (Exception e) {
            System.err.println("[Oddities][Shop] Failed to load catalog: " + e);
        }
    }

    public static synchronized void save() {
        try {
            Files.createDirectories(FILE.getParent());
            List<ShopEntry> list = new ArrayList<>(ENTRIES.values());
            Files.writeString(FILE, GSON.toJson(list), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[Oddities][Shop] Failed to save catalog: " + e);
        }
    }

    public static synchronized Collection<ShopEntry> entries() {
        return List.copyOf(ENTRIES.values());
    }

    public static synchronized ShopEntry get(String id) {
        return ENTRIES.get(id);
    }

    public static synchronized void upsert(ShopEntry entry) {
        ENTRIES.put(entry.id, entry);
    }

    public static synchronized boolean remove(String id) {
        return ENTRIES.remove(id) != null;
    }

    public static synchronized String toJsonForNetwork() {
        return GSON.toJson(new ArrayList<>(ENTRIES.values()));
    }

    public static void broadcastToOpenShops(MinecraftServer server) {
        server.getPlayerManager().getPlayerList().forEach(p -> {
            if (p.currentScreenHandler != null &&
                    p.currentScreenHandler.getClass().getName().endsWith("DabloonsMachineScreenHandler")) {
                ShopNetworking.sendCatalog(p);
                ShopNetworking.sendBalance(p);
            }
        });
    }

    private static void ensureExists() {
        try {
            if (Files.exists(FILE)) return;
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, defaultCatalogJson(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[Oddities][Shop] Failed to write default catalog: " + e);
        }
    }

    private static String defaultCatalogJson() {
        // starter catalog: random examples + cat preview that sells cat spawn egg
        List<ShopEntry> list = new ArrayList<>();

        list.add(entryItem("golden_apple", "Golden Apple", 25, "minecraft:golden_apple"));
        list.add(entryItem("ender_pearl", "Ender Pearl", 8, "minecraft:ender_pearl"));
        list.add(entryItem("name_tag", "Name Tag", 40, "minecraft:name_tag"));
        list.add(entryItem("diamond_sword", "Diamond Sword", 120, "minecraft:diamond_sword"));

        ShopEntry cat = new ShopEntry();
        cat.id = "cat";
        cat.displayName = "Cat (Spawn Egg)";
        cat.price = 60;
        cat.giveItemId = "minecraft:cat_spawn_egg";
        cat.giveCount = 1;
        cat.previewType = ShopEntry.PreviewType.ENTITY;
        cat.previewEntityType = "minecraft:cat";
        list.add(cat);

        return GSON.toJson(list);
    }

    private static ShopEntry entryItem(String id, String name, int price, String itemId) {
        ShopEntry e = new ShopEntry();
        e.id = id;
        e.displayName = name;
        e.price = price;
        e.giveItemId = itemId;
        e.giveCount = 1;
        e.previewType = ShopEntry.PreviewType.ITEM;
        e.previewItemId = itemId;
        return e;
    }

    private ShopCatalogManager() {}
}
