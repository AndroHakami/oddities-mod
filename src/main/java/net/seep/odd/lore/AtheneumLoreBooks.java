package net.seep.odd.lore;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AtheneumLoreBooks {
    public static final int ATHENEUM_BOOK_CMD = 2101;

    public record Volume(String id, String title, String author, String question, String[] answers, int correctAnswer, String[] pages) {}

    private static final Map<String, Volume> VOLUMES = new LinkedHashMap<>();
    private static final List<String> IDS = new ArrayList<>();

    static {
        add(new Volume(
                "star_volume_1",
                "Star Log [1/6]",
                "Curator Solenne",
                "What keeps the ceiling constellations awake?",
                new String[]{"Clockwork lanterns", "A listening bell", "A hidden choir", "Captured starlight"},
                3,
                new String[]{
                        json("The Atheneum ceiling does not reflect the sky. It remembers it."),
                        json("Every third stair holds a glass star under the stone."),
                        json("The oldest stars are fed with captured starlight and never truly sleep.")
                }
        ));
        add(new Volume(
                "star_volume_2",
                "Watcher Notes [2/6]",
                "Archivist Vey",
                "Which hall is said to whisper names back to you?",
                new String[]{"The Lantern Hall", "The Mirror Gallery", "The Southern Spiral", "The Silent Annex"},
                2,
                new String[]{
                        json("The Southern Spiral is the only stair that answers footsteps."),
                        json("If you speak softly there, it may return a different name."),
                        json("No maps agree on how many turns it truly has.")
                }
        ));
        add(new Volume(
                "star_volume_3",
                "Margins of Dust [3/6]",
                "Iris Pell",
                "What do the dust motes gather around in the eastern aisles?",
                new String[]{"Broken globes", "Warm books", "Silver chains", "Moonwater bowls"},
                1,
                new String[]{
                        json("The eastern aisles are warmer than the rest of the vault."),
                        json("Dust does not drift there. It gathers around warm books as if listening."),
                        json("Some shelves smell faintly of rain even in dry weather.")
                }
        ));
        add(new Volume(
                "star_volume_4",
                "Index of Bells [4/6]",
                "Mira Tal",
                "How many bells ring when the west stacks are sealed?",
                new String[]{"One", "Two", "Three", "Seven"},
                1,
                new String[]{
                        json("The west stacks seal with two clear bell tones."),
                        json("The first warns the living. The second warns the books."),
                        json("Anyone hearing a third bell should leave at once.")
                }
        ));
        add(new Volume(
                "star_volume_5",
                "A Quiet Atlas [5/6]",
                "Nero Vale",
                "What color is the ink used for forbidden shelf marks?",
                new String[]{"Red", "Gold", "Blue-black", "White"},
                2,
                new String[]{
                        json("Forbidden shelf marks are never drawn in ordinary black ink."),
                        json("They use a blue-black mixture that only shines under star lamps."),
                        json("The color fades by daylight so wandering hands pass them by.")
                }
        ));
        add(new Volume(
                "star_volume_6",
                "Underlight Manual [6/6]",
                "Sera Quill",
                "What must you never do in the underlight reading room?",
                new String[]{"Whistle", "Carry iron", "Read aloud", "Open two books at once"},
                2,
                new String[]{
                        json("The underlight room bends sound strangely after midnight."),
                        json("Reading aloud there causes the corners to answer back."),
                        json("The safest rule is silence, then leave before the lamps dim.")
                }
        ));
    }

    private AtheneumLoreBooks() {}

    private static void add(Volume volume) {
        VOLUMES.put(volume.id(), volume);
        IDS.add(volume.id());
    }

    private static String json(String text) {
        return "{\"text\":\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\",\"italic\":false}";
    }

    public static List<String> ids() {
        return IDS;
    }

    public static String randomId(Random random) {
        return IDS.get(random.nextInt(IDS.size()));
    }

    @Nullable
    public static Volume get(String id) {
        return VOLUMES.get(id);
    }

    public static String titleOf(String id) {
        Volume volume = get(id);
        return volume == null ? "Unknown Volume" : volume.title();
    }

    public static ItemStack createRandomBook(Random random) {
        return createBook(randomId(random));
    }

    public static ItemStack createBook(String id) {
        Volume volume = get(id);
        if (volume == null) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putString("title", volume.title());
        nbt.putString("author", volume.author());
        nbt.putInt("generation", 0);
        nbt.putBoolean("resolved", true);
        nbt.putInt("CustomModelData", ATHENEUM_BOOK_CMD);
        nbt.putString("oddLoreId", volume.id());

        NbtList pages = new NbtList();
        for (String page : volume.pages()) {
            pages.add(NbtString.of(page));
        }
        nbt.put("pages", pages);
        return stack;
    }

    public static boolean matchesRequested(ItemStack stack, String requestedId) {
        if (stack.isEmpty() || !stack.isOf(Items.WRITTEN_BOOK)) return false;
        Volume volume = get(requestedId);
        if (volume == null) return false;
        NbtCompound nbt = stack.getNbt();
        if (nbt == null) return false;
        if (!nbt.contains("CustomModelData") || nbt.getInt("CustomModelData") != ATHENEUM_BOOK_CMD) return false;
        if (nbt.contains("oddLoreId")) {
            return requestedId.equals(nbt.getString("oddLoreId"));
        }
        return volume.title().equals(nbt.getString("title"));
    }
}
