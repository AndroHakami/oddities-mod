// src/main/java/net/seep/odd/abilities/chef/RecipeBookContent.java
package net.seep.odd.abilities.chef;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.text.Text;

/**
 * Chef Recipe Book content.
 *
 * Auto-filled from your Super Cooker recipe JSONs (recipesCHEF.zip).
 * If you change the recipe JSONs, regenerate this file (or ask me to refresh it).
 */
public final class RecipeBookContent {
    private RecipeBookContent() {}

    public static final String TITLE  = "Chef's Recipe Book";
    public static final String AUTHOR = "Chef";

    private record Seg(String text, String color, boolean bold) {}
    private static Seg s(String t) { return new Seg(t, null, false); }
    private static Seg c(String t, String col) { return new Seg(t, col, false); }
    private static Seg b(String t, String col) { return new Seg(t, col, true); }
    private static Seg nl() { return s("\n"); }
    private static Seg sep() { return c("\n--------------------------------\n", "dark_gray"); }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "")
                .replace("\n", "\\n");
    }

    private static String page(Seg... segs) {
        StringBuilder sb = new StringBuilder(2048);
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

    private static Seg[] recipe(String name, String color, String vibe, String effect,
                                String ing1, String ing2, String ing3) {
        return new Seg[] {
                b(name + "\n", color),
                c(vibe + "\n", "gray"),
                nl(),
                b("Ingredients:\n", "dark_gray"),
                s("  1) " + ing1 + "\n"),
                s("  2) " + ing2 + "\n"),
                s("  3) " + ing3 + "\n"),
                nl(),
                b("Method:\n", "dark_gray"),
                s("  • Add ingredients to the cooktop.\n"),
                s("  • Fuel the furnace side.\n"),
                s("  • Stir on-beat to avoid Mush.\n"),
                nl(),
                b("Effect: ", "dark_gray"),
                s(effect + "\n")
        };
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

    private static final String[] PAGES_JSON = new String[] {
            page(concat(
                    b("CHEF'S RECIPE BOOK\n", "gold"),
                    c("All Super Cooker recipes — auto-filled from your recipe JSONs.\n\n", "gray"),
                    b("How to cook:\n", "dark_gray"),
                    s("• Place ingredients on the cooktop.\n"),
                    s("• Fuel the furnace side.\n"),
                    s("• Stir on the timing window to avoid Mush.\n"),
                    sep(),
                    b("Notes:\n", "dark_gray"),
                    s("• Effects apply to whoever eats the dish.\n"),
                    s("• Some effects are delayed or reactive.\n")
            )),

            page(concat(
                    b("KEBABS\n", "light_purple"),
                    recipe("Amethyst Kebab", "light_purple",
                            "A shimmering kebab that makes the world glow back.",
                            "Glowing + Night Vision for 5 minutes.",
                            "Amethyst Shard", "Wheat", "Bread"),
                    sep(),
                    recipe("Creeper Kebab", "green",
                            "Suspiciously spicy. Handle with swagger.",
                            "After 30 seconds: a strong NON-destructive blast around you (you are not harmed).",
                            "Gunpowder", "Porkchop", "Bread")
            )),

            page(concat(
                    b("BURGERS & SANDWICHES\n", "red"),
                    recipe("Hellish Burger", "dark_red",
                            "Forged in heat. The bite fights back.",
                            "Fire Resistance + Resistance I for 10 minutes.",
                            "Blaze Powder", "Beef", "Bread"),
                    sep(),
                    recipe("Radical Burger", "dark_purple",
                            "Looks dangerous. Tastes normal.",
                            "No special effect (pure style).",
                            "Beetroot", "Beef", "Bread"),
                    sep(),
                    recipe("Crappy Burger", "gray",
                            "A culinary mistake that bends space.",
                            "On eat: Teleport ~8 blocks in the direction you’re looking.",
                            "Rotten Flesh", "Beef", "Bread"),
                    sep(),
                    recipe("Egg Sandwich", "yellow",
                            "Simple comfort. Perfect between bigger dishes.",
                            "No special effect.",
                            "Egg", "Bread", "Bread")
            )),

            page(concat(
                    b("SEAFOOD\n", "aqua"),
                    recipe("Calamari", "aqua",
                            "Crisp rings for deep dives and fast swims.",
                            "Water Breathing + Dolphin’s Grace for 5 minutes.",
                            "Ink Sac", "Cod", "Bowl"),
                    sep(),
                    recipe("Sushi", "yellow",
                            "Clean and classic. The Chef’s baseline.",
                            "No special effect.",
                            "Kelp", "Salmon", "Bowl"),
                    sep(),
                    recipe("Puffer Sushi", "dark_green",
                            "A dare wrapped in rice.",
                            "Poison II for 10 seconds.",
                            "Pufferfish", "Kelp", "Bowl"),
                    sep(),
                    recipe("Squid Squash", "dark_aqua",
                            "Inky sweetness from the abyss.",
                            "Conduit Power for 3 minutes.",
                            "Sea Pickle", "Ink Sac", "Bowl")
            )),

            page(concat(
                    b("FRIES & SIDES\n", "gold"),
                    recipe("Fries", "gold",
                            "Fast food. Faster legs.",
                            "Speed V for 1 minute, then Slowness I for 2 minutes.",
                            "Potato", "Potato", "Coal"),
                    sep(),
                    recipe("Ghast Fries", "white",
                            "So light they almost don’t exist.",
                            "Invisibility + Speed II for 3 minutes.",
                            "Ghast Tear", "Potato", "Coal"),
                    sep(),
                    recipe("Deepdark Fries", "dark_aqua",
                            "Crunches like ancient stone. Echoes like a scream.",
                            "For 1 minute: your swings emit piercing sonic booms that deal small damage.",
                            "Echo Shard", "Potato", "Coal"),
                    sep(),
                    recipe("Chicken Balls", "gold",
                            "Little bites. Big morale.",
                            "No special effect.",
                            "Chicken", "Chicken", "Chicken")
            )),

            page(concat(
                    b("ICE CREAMS\n", "aqua"),
                    recipe("Magma Icecream", "gold",
                            "Cold on the tongue. Hot in the soul.",
                            "Fire Resistance for 10 minutes.",
                            "Magma Cream", "Snowball", "Bowl"),
                    sep(),
                    recipe("Shulker Icecream", "light_purple",
                            "Floaty sweetness with a dangerous aftertaste.",
                            "Levitation II + No fall damage for 1 minute.",
                            "Shulker Shell", "Snowball", "Bowl"),
                    sep(),
                    recipe("Outer Icecream", "aqua",
                            "Starlight sugar and bad decisions.",
                            "For 1 minute: anything you hit gets Levitation III for 1 second.",
                            "End Rod", "Snowball", "Bowl"),
                    sep(),
                    recipe("Yanbu Icecream", "blue",
                            "Illegal amounts of sugar.",
                            "Speed XX for 10 seconds.",
                            "Sugar", "Sugar", "Snowball")
            )),

            page(concat(
                    b("MAINS & SPECIALS (I)\n", "red"),
                    recipe("Dragon Burrito", "red",
                            "A wrap packed with pure momentum.",
                            "Strength II + No fall damage + Triple Jump for 5 minutes.",
                            "Dragon Breath", "Chicken", "Bread"),
                    sep(),
                    recipe("Ramen", "gold",
                            "Broth. Noodles. Peace.",
                            "No special effect.",
                            "Bowl", "Dried Kelp", "Mushroom Stew"),
                    sep(),
                    recipe("Emerald Pie", "green",
                            "A lucky slice that pays back later.",
                            "Luck for 30 minutes.",
                            "Emerald", "Wheat", "Sugar")
            )),

            page(concat(
                    b("MAINS & SPECIALS (II)\n", "red"),
                    recipe("Masoub", "yellow",
                            "Sweet fuel with a heroic kick.",
                            "Jump Boost III + Resistance II for 3 minutes.",
                            "Honey Bottle", "Bread", "Golden Apple"),
                    sep(),
                    recipe("Miner Berries", "dark_green",
                            "Crunchy berries that feel like a pickaxe.",
                            "Haste II for 10 minutes.",
                            "Sweet Berries", "Iron Ingot", "Coal"),
                    sep(),
                    recipe("Hearty Stew", "gold",
                            "A warm bowl that keeps you moving.",
                            "No special effect (comfort meal).",
                            "Bowl", "Beef", "Carrot")
            )),

            page(concat(
                    b("DISASTER DISH\n", "gray"),
                    b("Mush\n", "dark_gray"),
                    c("When rhythm fails… the pot fights you back.\n\n", "gray"),
                    b("Effect: ", "dark_gray"),
                    s("Bad cook result. (Consumes ingredients.)\n"),
                    nl(),
                    c("Tip: Mush means your timing was off. Try again.\n", "dark_gray")
            ))
    };

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