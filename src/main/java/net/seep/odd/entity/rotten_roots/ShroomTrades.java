// FILE: src/main/java/net/seep/odd/entity/rotten_roots/ShroomTrades.java
package net.seep.odd.entity.rotten_roots;

import com.google.gson.*;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.seep.odd.Oddities;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Datapack-driven trades.
 *
 * Files:
 *  data/<namespace>/shroom_trades/*.json
 *
 * Each file can target a profile: odd:shroom or odd:elder_shroom.
 */
public final class ShroomTrades {
    private ShroomTrades() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Identifier RELOAD_ID = new Identifier(Oddities.MOD_ID, "shroom_trades");
    private static int VERSION = 0;

    // profileId -> pool
    private static final Map<Identifier, TradePool> POOLS = new HashMap<>();

    public static void init() {
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return RELOAD_ID;
            }

            @Override
            public void reload(ResourceManager manager) {
                loadAll(manager);
            }
        });
    }

    public static int version() {
        return VERSION;
    }

    public static TradeOfferList buildOffers(Identifier profileId, Random random) {
        TradeOfferList out = new TradeOfferList();
        TradePool pool = POOLS.get(profileId);
        if (pool == null) return out;

        if (pool.rolls <= 0 || pool.entries.isEmpty()) {
            for (OfferEntry e : pool.entries) out.add(e.toOffer(random));
            return out;
        }

        // weighted, distinct picks
        List<OfferEntry> bag = new ArrayList<>(pool.entries);
        int rolls = Math.min(pool.rolls, bag.size());

        for (int i = 0; i < rolls; i++) {
            OfferEntry picked = pickWeighted(bag, random);
            if (picked == null) break;
            out.add(picked.toOffer(random));
            bag.remove(picked);
        }

        return out;
    }

    private static OfferEntry pickWeighted(List<OfferEntry> entries, Random random) {
        int total = 0;
        for (OfferEntry e : entries) total += Math.max(1, e.weight);

        if (total <= 0) return null;

        int r = random.nextInt(total);
        int acc = 0;
        for (OfferEntry e : entries) {
            acc += Math.max(1, e.weight);
            if (r < acc) return e;
        }
        return entries.get(entries.size() - 1);
    }

    private static void loadAll(ResourceManager manager) {
        POOLS.clear();

        // Pull every JSON under data/*/shroom_trades/
        Map<Identifier, Resource> found = manager.findResources("shroom_trades", id -> id.getPath().endsWith(".json"));

        for (Map.Entry<Identifier, Resource> entry : found.entrySet()) {
            Identifier fileId = entry.getKey();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(entry.getValue().getInputStream(), StandardCharsets.UTF_8))) {
                JsonElement rootEl = JsonParser.parseReader(reader);
                if (!rootEl.isJsonObject()) continue;

                JsonObject root = rootEl.getAsJsonObject();

                Identifier profile = root.has("profile")
                        ? new Identifier(JsonHelper.getString(root, "profile"))
                        : guessProfileFromFilename(fileId);

                int rolls = JsonHelper.getInt(root, "rolls", 0);
                JsonArray offers = JsonHelper.getArray(root, "offers");

                TradePool pool = POOLS.computeIfAbsent(profile, k -> new TradePool());
                if (rolls > 0) pool.rolls = Math.max(pool.rolls, rolls); // allow multiple files; keep max rolls

                for (JsonElement offerEl : offers) {
                    if (!offerEl.isJsonObject()) continue;
                    OfferEntry def = parseOffer(offerEl.getAsJsonObject());
                    if (def != null) pool.entries.add(def);
                }

            } catch (Exception e) {
                Oddities.LOGGER.warn("[ShroomTrades] Failed reading {}: {}", fileId, e.toString());
            }
        }

        VERSION++;
        Oddities.LOGGER.info("[ShroomTrades] Loaded {} trade profile(s). version={}", POOLS.size(), VERSION);
    }

    private static Identifier guessProfileFromFilename(Identifier fileId) {
        // e.g. data/odd/shroom_trades/shroom.json -> odd:shroom
        String path = fileId.getPath();
        String name = path.substring(path.lastIndexOf('/') + 1).replace(".json", "");
        return new Identifier(Oddities.MOD_ID, name);
    }

    private static OfferEntry parseOffer(JsonObject obj) {
        ItemStack buyA = parseStack(JsonHelper.getObject(obj, "buy"));
        ItemStack sell = parseStack(JsonHelper.getObject(obj, "sell"));

        ItemStack buyB = ItemStack.EMPTY;
        if (obj.has("buyB") && obj.get("buyB").isJsonObject()) {
            buyB = parseStack(obj.getAsJsonObject("buyB"));
        }

        int maxUses = JsonHelper.getInt(obj, "maxUses", 12);
        int xp = JsonHelper.getInt(obj, "xp", 2);
        float priceMul = JsonHelper.getFloat(obj, "priceMultiplier", 0.05f);
        int weight = JsonHelper.getInt(obj, "weight", 1);

        return new OfferEntry(buyA, buyB, sell, maxUses, xp, priceMul, weight);
    }

    private static ItemStack parseStack(JsonObject obj) {
        String itemStr = JsonHelper.getString(obj, "item");
        Identifier itemId = new Identifier(itemStr);
        Item item = Registries.ITEM.get(itemId);

        int count = JsonHelper.getInt(obj, "count", 1);
        ItemStack stack = new ItemStack(item, count);

        if (obj.has("nbt")) {
            String snbt = JsonHelper.getString(obj, "nbt");
            try {
                NbtCompound nbt = StringNbtReader.parse(snbt);
                stack.setNbt(nbt);
            } catch (CommandSyntaxException e) {
                Oddities.LOGGER.warn("[ShroomTrades] Bad NBT '{}' for item {}: {}", snbt, itemId, e.getMessage());
            }
        }

        return stack;
    }

    private static final class TradePool {
        int rolls = 0; // 0 = add all
        final List<OfferEntry> entries = new ArrayList<>();
    }

    private static final class OfferEntry {
        final ItemStack buyA;
        final ItemStack buyB;
        final ItemStack sell;
        final int maxUses;
        final int xp;
        final float priceMultiplier;
        final int weight;

        OfferEntry(ItemStack buyA, ItemStack buyB, ItemStack sell, int maxUses, int xp, float priceMultiplier, int weight) {
            this.buyA = buyA;
            this.buyB = buyB;
            this.sell = sell;
            this.maxUses = maxUses;
            this.xp = xp;
            this.priceMultiplier = priceMultiplier;
            this.weight = weight;
        }

        TradeOffer toOffer(Random random) {
            if (buyB == null || buyB.isEmpty()) {
                return new TradeOffer(buyA.copy(), sell.copy(), maxUses, xp, priceMultiplier);
            }
            return new TradeOffer(buyA.copy(), buyB.copy(), sell.copy(), maxUses, xp, priceMultiplier);
        }
    }
}