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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ShopCatalogManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<ShopEntry>>(){}.getType();

    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve(Oddities.MOD_ID)
            .resolve("dabloons_shop.json");

    private static final Map<String, ShopEntry> ENTRIES = new LinkedHashMap<>();

    private static final Comparator<ShopEntry> ENTRY_ORDER = Comparator
            .comparing((ShopEntry e) -> e.category == null ? ShopEntry.Category.MISC.ordinal() : e.category.ordinal())
            .thenComparingInt(e -> e.sortOrder)
            .thenComparing(e -> e.displayName == null ? "" : e.displayName.toLowerCase())
            .thenComparing(e -> e.id == null ? "" : e.id.toLowerCase());

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
                    normalize(e);
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
            Files.writeString(FILE, GSON.toJson(sortedEntries()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[Oddities][Shop] Failed to save catalog: " + e);
        }
    }

    public static synchronized Collection<ShopEntry> entries() {
        return List.copyOf(sortedEntries());
    }

    public static synchronized ShopEntry get(String id) {
        return ENTRIES.get(id);
    }

    public static synchronized void upsert(ShopEntry entry) {
        normalize(entry);
        ENTRIES.put(entry.id, entry);
    }

    public static synchronized boolean remove(String id) {
        return ENTRIES.remove(id) != null;
    }

    public static synchronized String toJsonForNetwork() {
        return GSON.toJson(sortedEntries());
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

    private static List<ShopEntry> sortedEntries() {
        List<ShopEntry> list = new ArrayList<>(ENTRIES.values());
        list.sort(ENTRY_ORDER);
        return list;
    }

    private static void normalize(ShopEntry e) {
        if (e.displayName == null || e.displayName.isBlank()) {
            e.displayName = e.id;
        }
        if (e.description == null) {
            e.description = "";
        }
        if (e.category == null) {
            e.category = ShopEntry.Category.MISC;
        }
        if (e.grantType == null) {
            e.grantType = ShopEntry.GrantType.ITEM;
        }
        if (e.previewType == null) {
            e.previewType = ShopEntry.PreviewType.ITEM;
        }
        if (e.giveItemId == null || e.giveItemId.isBlank()) {
            e.giveItemId = "minecraft:stone";
        }
        if (e.giveCount <= 0) {
            e.giveCount = 1;
        }
        if (e.previewItemId == null) {
            e.previewItemId = "";
        }
        if (e.previewEntityType == null) {
            e.previewEntityType = "";
        }
        if (e.grantCommand == null) {
            e.grantCommand = "";
        }
        if (e.price < 0) {
            e.price = 0;
        }
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
        List<ShopEntry> list = new ArrayList<>();

        list.add(entryItem("bronze_sword", "Bronze Sword", 25, ShopEntry.Category.WEAPONS, 0, "minecraft:iron_sword"));
        list.add(entryItem("seal_pet", "Seal", 200, ShopEntry.Category.PETS, 0, "minecraft:axolotl_spawn_egg", "minecraft:axolotl", "A chill companion that is ready for its first free name."));
        list.add(entryCommand("sparkle_style", "Sparkle Trail", 120, ShopEntry.Category.STYLES, 0, "minecraft:nether_star", "title {player} actionbar {\"text\":\"Style unlocked!\",\"color\":\"gold\"}", "A flashy cosmetic unlock for showing off in style."));
        list.add(entryItem("golden_apple", "Golden Apple", 30, ShopEntry.Category.MISC, 0, "minecraft:golden_apple"));

        return GSON.toJson(list);
    }

    private static ShopEntry entryItem(String id, String name, int price, ShopEntry.Category category, int sort, String itemId) {
        return entryItem(id, name, price, category, sort, itemId, null, "");
    }



    private static ShopEntry entryItem(String id, String name, int price, ShopEntry.Category category, int sort, String itemId, String previewEntityId) {
        return entryItem(id, name, price, category, sort, itemId, previewEntityId, "");
    }

    private static ShopEntry entryItem(String id, String name, int price, ShopEntry.Category category, int sort, String itemId, String previewEntityId, String description) {
        ShopEntry e = new ShopEntry();
        e.id = id;
        e.displayName = name;
        e.description = description == null ? "" : description;
        e.price = price;
        e.category = category;
        e.sortOrder = sort;
        e.giveItemId = itemId;
        e.giveCount = 1;
        e.pet = category == ShopEntry.Category.PETS;
        e.grantType = ShopEntry.GrantType.ITEM;
        if (previewEntityId != null && !previewEntityId.isBlank()) {
            e.previewType = ShopEntry.PreviewType.ENTITY;
            e.previewEntityType = previewEntityId;
            e.previewItemId = "";
        } else {
            e.previewType = ShopEntry.PreviewType.ITEM;
            e.previewItemId = itemId;
            e.previewEntityType = "";
        }
        return e;
    }

    private static ShopEntry entryCommand(String id, String name, int price, ShopEntry.Category category, int sort, String previewItemId, String command, String description) {
        ShopEntry e = new ShopEntry();
        e.id = id;
        e.displayName = name;
        e.description = description == null ? "" : description;
        e.price = price;
        e.category = category;
        e.sortOrder = sort;
        e.grantType = ShopEntry.GrantType.COMMAND;
        e.grantCommand = command;
        e.previewType = ShopEntry.PreviewType.ITEM;
        e.previewItemId = previewItemId;
        e.giveItemId = previewItemId;
        e.giveCount = 1;
        return e;
    }

    private ShopCatalogManager() {}
}
