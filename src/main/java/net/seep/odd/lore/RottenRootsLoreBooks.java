package net.seep.odd.lore;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;

public final class RottenRootsLoreBooks {
    private RottenRootsLoreBooks() {}

    // All Rotten Roots books use the same custom model/texture
    public static final int ROTTEN_BOOK_CMD = 2001;

    public static ItemStack createRottenLogs1() {
        return makeBook(
                "Rotten Logs [1/6]",
                "Aveline Rook",
                ROTTEN_BOOK_CMD,

                // PAGE 1
                "{\"text\":\"The roots breathe when no one is looking.\",\"italic\":false}",

                // PAGE 2
                "{\"text\":\"Glow sap gathers near the lower hollows in thicker strands than before.\",\"italic\":false}",

                // PAGE 3
                "{\"text\":\"I marked one bundle with twine. By morning, it had moved three spans to the east.\",\"italic\":false}"
        );
    }

    public static ItemStack createExperimentLog1() {
        return makeBook(
                "Experiment Log I",
                "Aveline Rook",
                ROTTEN_BOOK_CMD,
                "{\"text\":\"The sap reacted before the incision was made.\",\"italic\":false}",
                "{\"text\":\"Roots near the lower chambers bend toward heat and singing.\",\"italic\":false}"
        );
    }

    public static ItemStack createFieldNotes2() {
        return makeBook(
                "Field Notes II",
                "Marrow Vane",
                ROTTEN_BOOK_CMD,
                "{\"text\":\"Several of the hanging roots are hollow.\",\"italic\":false}",
                "{\"text\":\"I heard tapping from inside one after the lantern went out.\",\"italic\":false}"
        );
    }

    public static ItemStack createSpecimenJournal() {
        return makeBook(
                "Specimen Journal",
                "Iris Vale",
                ROTTEN_BOOK_CMD,
                "{\"text\":\"Specimen 4 survived the poison bath longer than expected.\",\"italic\":false}",
                "{\"text\":\"Do not place recovered samples near glow sap storage.\",\"italic\":false}"
        );
    }

    private static ItemStack makeBook(String title, String author, int customModelData, String... jsonPages) {
        ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);

        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putString("title", title);
        nbt.putString("author", author);
        nbt.putInt("generation", 0);
        nbt.putBoolean("resolved", true);
        nbt.putInt("CustomModelData", customModelData);

        NbtList pages = new NbtList();
        for (String pageJson : jsonPages) {
            pages.add(NbtString.of(pageJson));
        }
        nbt.put("pages", pages);

        return stack;
    }
}