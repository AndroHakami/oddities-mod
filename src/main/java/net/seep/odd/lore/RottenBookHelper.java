package net.seep.odd.lore;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;

public final class RottenBookHelper {
    private RottenBookHelper() {}

    public static final int ROTTEN_BOOK_CMD = 2001;

    public static ItemStack makeRottenBook(String title, String author, String... jsonPages) {
        ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);

        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putString("title", title);
        nbt.putString("author", author);
        nbt.putInt("generation", 0);
        nbt.putBoolean("resolved", true);
        nbt.putInt("CustomModelData", ROTTEN_BOOK_CMD);

        NbtList pages = new NbtList();
        for (String page : jsonPages) {
            pages.add(NbtString.of(page));
        }
        nbt.put("pages", pages);

        return stack;
    }
}