// src/main/java/net/seep/odd/abilities/chef/RecipeBookContent.java
package net.seep.odd.abilities.chef;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Chef recipe book content, synced to the real Super Cooker JSON recipes.
 * Uses the lang tooltip text as the recipe flavor line.
 */
public final class RecipeBookContent {
    private RecipeBookContent() {}

    public static final String TITLE  = "Chef's Recipe Book";
    public static final String AUTHOR = "Chef";

    private record Seg(String text, String color, boolean bold) {}
    private record RecipeEntry(
            String category,
            String name,
            String color,
            String description,
            String effect,
            String resultName,
            int resultCount,
            int cookTimeTicks,
            int minStirs,
            String ing1,
            String ing2,
            String ing3
    ) {}

    private static Seg s(String t) { return new Seg(t, null, false); }
    private static Seg c(String t, String col) { return new Seg(t, col, false); }
    private static Seg b(String t, String col) { return new Seg(t, col, true); }
    private static Seg nl() { return s("\n"); }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "")
                .replace("\n", "\\n");
    }

    private static String page(Seg... segs) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("{\"text\":\"\",\"extra\":[");
        for (int i = 0; i < segs.length; i++) {
            Seg g = segs[i];
            if (i != 0) sb.append(',');
            sb.append("{\"text\":\"").append(esc(g.text)).append("\"");
            if (g.color != null) sb.append(",\"color\":\"").append(g.color).append("\"");
            if (g.bold) sb.append(",\"bold\":true");
            sb.append('}');
        }
        sb.append("]}");
        return sb.toString();
    }

    private static Seg[] concat(Object... parts) {
        int count = 0;
        for (Object o : parts) {
            if (o instanceof Seg) count++;
            else if (o instanceof Seg[] arr) count += arr.length;
        }
        Seg[] out = new Seg[count];
        int i = 0;
        for (Object o : parts) {
            if (o instanceof Seg g) out[i++] = g;
            else if (o instanceof Seg[] arr) for (Seg g : arr) out[i++] = g;
        }
        return out;
    }

    private static String formatCookTime(int ticks) {
        double seconds = ticks / 20.0;
        String s = String.format(Locale.ROOT, "%.1f", seconds);
        if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
        return s + "s";
    }

    private static Seg[] recipePage(RecipeEntry r) {
        return new Seg[] {
                c(r.category().toUpperCase(Locale.ROOT) + "\n", "dark_gray"),
                b(r.name() + "\n", r.color()),
                c(r.description() + "\n", "gray"),
                nl(),
                b("Ingredients:\n", "dark_gray"),
                s("1) " + r.ing1() + "\n"),
                s("2) " + r.ing2() + "\n"),
                s("3) " + r.ing3() + "\n"),
                nl(),
                b("Makes: ", "dark_gray"),
                s(r.resultName() + " x" + r.resultCount() + "\n"),
                b("Cook: ", "dark_gray"),
                s(formatCookTime(r.cookTimeTicks()) + "   "),
                b("Stirs: ", "dark_gray"),
                s(String.valueOf(r.minStirs()) + "\n"),
                b("Effect: ", "dark_gray"),
                s(r.effect())
        };
    }

    private static final RecipeEntry[] RECIPES = new RecipeEntry[] {
            new RecipeEntry(
                    "Kebabs",
                    "Amethyst Kebab",
                    "light_purple",
                    "Crystal kebab, the good type of crystal!! ★★★",
                    "Glowing + Night Vision for 5 minutes.",
                    "Amethyst Kebab",
                    1,
                    200,
                    2,
                    "Amethyst Shard",
                    "Wheat",
                    "Bread"
            ),
            new RecipeEntry(
                    "Kebabs",
                    "Creeper Kebab",
                    "green",
                    "Spicy… with a delayed surprise!. ★★★",
                    "After 30 seconds: a strong NON-destructive blast around you (you are not harmed).",
                    "Creeper Kebab",
                    3,
                    300,
                    3,
                    "Creeper Head",
                    "Stick",
                    "Gunpowder"
            ),
            new RecipeEntry(
                    "Burgers & Sandwiches",
                    "Hellish Burger",
                    "dark_red",
                    "From the depths of the nether, banned in the aether. ★★★",
                    "Fire Resistance + Resistance I for 10 minutes.",
                    "Hellish Burger",
                    1,
                    300,
                    3,
                    "Bread",
                    "Nether Wart",
                    "Beef"
            ),
            new RecipeEntry(
                    "Burgers & Sandwiches",
                    "Radical Burger",
                    "dark_purple",
                    "ITS SO DAMN COOL.. ★★☆",
                    "No special effect.",
                    "Radical Burger",
                    1,
                    400,
                    4,
                    "Bread",
                    "Chicken",
                    "Beef"
            ),
            new RecipeEntry(
                    "Burgers & Sandwiches",
                    "Crappy Burger",
                    "gray",
                    "Not so Crappy! Teleports you. ★★☆",
                    "On eat: teleport about 8 blocks in the direction you are looking.",
                    "Crappy Burger",
                    1,
                    300,
                    3,
                    "Bread",
                    "Ender Pearl",
                    "Beef"
            ),
            new RecipeEntry(
                    "Burgers & Sandwiches",
                    "Egg Sandwich",
                    "yellow",
                    "Simple comfort food. ★★☆",
                    "No special effect.",
                    "Egg Sandwich",
                    1,
                    100,
                    5,
                    "Egg",
                    "Milk Bucket",
                    "Bread"
            ),
            new RecipeEntry(
                    "Seafood",
                    "Calamari",
                    "aqua",
                    "Seafood so good you are one with the ocean. ★★★",
                    "Water Breathing + Dolphin's Grace for 5 minutes.",
                    "Calamari",
                    1,
                    400,
                    4,
                    "Ink Sac",
                    "Cod",
                    "Bowl"
            ),
            new RecipeEntry(
                    "Seafood",
                    "Sushi",
                    "yellow",
                    "Clean and classic. Always solid. ★★☆",
                    "No special effect.",
                    "Sushi",
                    3,
                    200,
                    4,
                    "Dried Kelp",
                    "Salmon",
                    "Wheat"
            ),
            new RecipeEntry(
                    "Seafood",
                    "Puffer Sushi",
                    "dark_green",
                    "The greatest invention, eat quickly before it depletes!. ★★★",
                    "Poison II for 10 seconds.",
                    "Puffer Sushi",
                    3,
                    200,
                    4,
                    "Dried Kelp",
                    "Pufferfish",
                    "Wheat"
            ),
            new RecipeEntry(
                    "Seafood",
                    "Squid Squash",
                    "dark_aqua",
                    "Eat Squid, be Squid!. ★★☆",
                    "Conduit Power for 3 minutes.",
                    "Squid Squash",
                    1,
                    400,
                    4,
                    "Ink Sac",
                    "Glow Ink Sac",
                    "Bowl"
            ),
            new RecipeEntry(
                    "Fries & Sides",
                    "Fries",
                    "gold",
                    "Fast feet… then heavy legs. ★★☆",
                    "Speed V for 1 minute, then Slowness I for 2 minutes.",
                    "Fries",
                    2,
                    600,
                    2,
                    "Potato",
                    "Potato",
                    "Bowl"
            ),
            new RecipeEntry(
                    "Fries & Sides",
                    "Ghast Fries",
                    "white",
                    "The tears add a nice salt to it!. ★★☆",
                    "Invisibility + Speed II for 3 minutes.",
                    "Ghast Fries",
                    2,
                    600,
                    2,
                    "Potato",
                    "Ghast Tear",
                    "Bowl"
            ),
            new RecipeEntry(
                    "Fries & Sides",
                    "Deepdark Fries",
                    "dark_aqua",
                    "Echo-crunch fries. Makes you grow warden abilities! ★★★",
                    "For 1 minute: your swings emit piercing sonic booms that deal small damage.",
                    "Deepdark Fries",
                    2,
                    600,
                    2,
                    "Echo Shard",
                    "Potato",
                    "Bowl"
            ),
            new RecipeEntry(
                    "Fries & Sides",
                    "Chicken Balls",
                    "gold",
                    "Protein Protein, filling!. ★★☆",
                    "No special effect.",
                    "Chicken Balls",
                    3,
                    400,
                    4,
                    "Chicken",
                    "Chicken",
                    "Chicken"
            ),
            new RecipeEntry(
                    "Ice Creams",
                    "Magma Icecream",
                    "gold",
                    "Cold scoop, hot soul. ★★☆",
                    "Fire Resistance for 10 minutes.",
                    "Magma Icecream",
                    3,
                    100,
                    4,
                    "Milk Bucket",
                    "Magma Cream",
                    "Sugar"
            ),
            new RecipeEntry(
                    "Ice Creams",
                    "Shulker Icecream",
                    "light_purple",
                    "Floaty dessert. Land safely!. ★★☆",
                    "Levitation II + no fall damage for 1 minute.",
                    "Shulker Icecream",
                    3,
                    100,
                    4,
                    "Milk Bucket",
                    "Shulker Shell",
                    "Sugar"
            ),
            new RecipeEntry(
                    "Ice Creams",
                    "Outer Icecream",
                    "aqua",
                    "Who knew those aliens tasted so damn good? ★★☆",
                    "For 1 minute: anything you hit gets Levitation III for 1 second.",
                    "Outer Icecream",
                    3,
                    100,
                    4,
                    "Milk Bucket",
                    "Alien Pearl",
                    "Sugar"
            ),
            new RecipeEntry(
                    "Ice Creams",
                    "Yanbu Icecream",
                    "blue",
                    "Why is this in the game... tastes good though!★★★",
                    "Speed XX for 10 seconds.",
                    "Yanbu Icecream",
                    3,
                    100,
                    4,
                    "Milk Bucket",
                    "Heart of the Sea",
                    "Sugar"
            ),
            new RecipeEntry(
                    "Mains & Specials",
                    "Dragon Burrito",
                    "red",
                    "Momentum wrapped in fire. ★★★",
                    "Strength II + no fall damage + triple jump for 5 minutes.",
                    "Dragon Burrito",
                    2,
                    400,
                    5,
                    "Dragon Breath",
                    "Chicken",
                    "Bread"
            ),
            new RecipeEntry(
                    "Mains & Specials",
                    "Ramen",
                    "gold",
                    "A Classic Comfort, Wish it was indomie though.... ★★☆",
                    "No special effect.",
                    "Ramen",
                    2,
                    150,
                    2,
                    "Egg",
                    "Milk Bucket",
                    "Wheat"
            ),
            new RecipeEntry(
                    "Mains & Specials",
                    "Emerald Pie",
                    "green",
                    "A lucky slice with rich vibes. ★★☆",
                    "Luck for 30 minutes.",
                    "Emerald Pie",
                    1,
                    500,
                    5,
                    "Sugar",
                    "Bread",
                    "Emerald"
            ),
            new RecipeEntry(
                    "Mains & Specials",
                    "Masoub",
                    "yellow",
                    "Who needs bananas when you have soul?. ★★☆",
                    "Jump Boost III + Resistance II for 3 minutes.",
                    "Masoub",
                    2,
                    300,
                    5,
                    "Sugar",
                    "Honeycomb",
                    "Bread"
            ),
            new RecipeEntry(
                    "Mains & Specials",
                    "Miner Berries",
                    "dark_green",
                    "Fills the stomach, lights the caves!. ★★★",
                    "Haste II for 10 minutes.",
                    "Miner Berries",
                    3,
                    200,
                    2,
                    "Glow Berries",
                    "Sugar",
                    "Cocoa Beans"
            ),
            new RecipeEntry(
                    "Mains & Specials",
                    "Hearty Stew",
                    "gold",
                    "A warm classic from the Super Cooker. ★★☆",
                    "No special effect.",
                    "Rabbit Stew",
                    1,
                    200,
                    3,
                    "Bowl",
                    "Beef",
                    "Carrot"
            )
    };

    private static final String[] PAGES_JSON;

    static {
        List<String> pages = new ArrayList<>();

        pages.add(page(concat(
                b("CHEF'S RECIPE BOOK\n", "gold"),
                c("Real Super Cooker recipes, synced to the actual recipe JSONs.\n\n", "gray"),
                b("How to cook:\n", "dark_gray"),
                s("• Place the 3 ingredients on the cooktop.\n"),
                s("• Fuel the furnace side.\n"),
                s("• Stir on the timing window or you get Mush.\n\n"),
                b("What changed:\n", "dark_gray"),
                s("• Ingredients now match the real Super Cooker files.\n"),
                s("• Flavor text now comes from the lang tooltips.\n"),
                s("• Yield, cook time and stir count are listed for every dish.")
        )));

        pages.add(page(concat(
                b("CONTENTS I\n", "gold"),
                b("Kebabs\n", "dark_gray"),
                s("• Amethyst Kebab\n"),
                s("• Creeper Kebab\n"),
                nl(),
                b("Burgers & Sandwiches\n", "dark_gray"),
                s("• Hellish Burger\n"),
                s("• Radical Burger\n"),
                s("• Crappy Burger\n"),
                s("• Egg Sandwich\n"),
                nl(),
                b("Seafood\n", "dark_gray"),
                s("• Calamari\n"),
                s("• Sushi\n"),
                s("• Puffer Sushi\n"),
                s("• Squid Squash\n"),
                nl(),
                b("Fries & Sides\n", "dark_gray"),
                s("• Fries\n"),
                s("• Ghast Fries\n"),
                s("• Deepdark Fries\n"),
                s("• Chicken Balls\n"),
                c("Continued on next page...", "gray")
        )));

        pages.add(page(concat(
                b("CONTENTS II\n", "gold"),
                b("Ice Creams\n", "dark_gray"),
                s("• Magma Icecream\n"),
                s("• Shulker Icecream\n"),
                s("• Outer Icecream\n"),
                s("• Yanbu Icecream\n"),
                nl(),
                b("Mains & Specials\n", "dark_gray"),
                s("• Dragon Burrito\n"),
                s("• Ramen\n"),
                s("• Emerald Pie\n"),
                s("• Masoub\n"),
                s("• Miner Berries\n"),
                s("• Hearty Stew\n"),
                nl(),
                b("Disaster Dish\n", "dark_gray"),
                s("• Mush\n"),
                nl(),
                c("One recipe per page after this for easy reading.", "gray")
        )));

        for (RecipeEntry recipe : RECIPES) {
            pages.add(page(recipePage(recipe)));
        }

        pages.add(page(concat(
                b("MUSH\n", "dark_gray"),
                c("When the rhythm fails, the pot fights back.\n\n", "gray"),
                b("Result: ", "dark_gray"),
                s("Bad cook outcome. Ingredients are still consumed.\n\n"),
                b("Tip: ", "dark_gray"),
                s("If you get Mush, your stirring timing was off. Slow down and hit the timing window.")
        )));

        PAGES_JSON = pages.toArray(new String[0]);
    }

    public static void applyToWrittenBook(ItemStack stack) {
        NbtCompound n = stack.getOrCreateNbt();
        n.putString("title", TITLE);
        n.putString("author", AUTHOR);
        n.putInt("generation", 0);
        n.putBoolean("resolved", true);

        NbtList pages = new NbtList();
        for (String json : PAGES_JSON) pages.add(NbtString.of(json));
        n.put("pages", pages);

        stack.setCustomName(Text.literal(TITLE));
    }
}
