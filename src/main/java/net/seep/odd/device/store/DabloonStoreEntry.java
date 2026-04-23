package net.seep.odd.device.store;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;

public final class DabloonStoreEntry {
    public static final int TITLE_MAX_LEN = 64;
    public static final int DESC_MAX_LEN = 180;

    private ItemStack stock;
    private String title;
    private String description;
    private int pricePerItem;

    public DabloonStoreEntry(ItemStack stock, String title, String description, int pricePerItem) {
        this.stock = stock == null ? ItemStack.EMPTY : stock.copy();
        this.title = sanitizeTitle(title, this.stock.isEmpty() ? "Store Item" : this.stock.getName().getString());
        this.description = sanitizeDescription(description);
        this.pricePerItem = Math.max(1, pricePerItem);
    }

    public ItemStack stock() {
        return stock;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public int pricePerItem() {
        return pricePerItem;
    }

    public int stockCount() {
        return stock.isEmpty() ? 0 : stock.getCount();
    }

    public boolean isEmpty() {
        return stock == null || stock.isEmpty() || stock.getCount() <= 0;
    }

    public void setMeta(String title, String description, int pricePerItem) {
        this.title = sanitizeTitle(title, stock.isEmpty() ? "Store Item" : stock.getName().getString());
        this.description = sanitizeDescription(description);
        this.pricePerItem = Math.max(1, pricePerItem);
    }

    public ItemStack takeOne() {
        if (isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack out = stock.copy();
        out.setCount(1);
        stock.decrement(1);
        return out;
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        if (!stock.isEmpty()) {
            nbt.put("Stock", stock.writeNbt(new NbtCompound()));
        }
        nbt.putString("Title", title);
        nbt.putString("Description", description);
        nbt.putInt("PricePerItem", pricePerItem);
        return nbt;
    }

    public static DabloonStoreEntry fromNbt(NbtCompound nbt) {
        ItemStack stack = nbt.contains("Stock") ? ItemStack.fromNbt(nbt.getCompound("Stock")) : ItemStack.EMPTY;
        return new DabloonStoreEntry(
                stack,
                nbt.getString("Title"),
                nbt.getString("Description"),
                Math.max(1, nbt.getInt("PricePerItem"))
        );
    }

    public void write(PacketByteBuf buf) {
        buf.writeItemStack(stock);
        buf.writeString(title, TITLE_MAX_LEN);
        buf.writeString(description, DESC_MAX_LEN);
        buf.writeVarInt(pricePerItem);
    }

    public static DabloonStoreEntry read(PacketByteBuf buf) {
        return new DabloonStoreEntry(
                buf.readItemStack(),
                buf.readString(TITLE_MAX_LEN),
                buf.readString(DESC_MAX_LEN),
                Math.max(1, buf.readVarInt())
        );
    }

    private static String sanitizeTitle(String title, String fallback) {
        String value = title == null ? "" : title.trim();
        if (value.isBlank()) value = fallback;
        if (value.length() > TITLE_MAX_LEN) {
            value = value.substring(0, TITLE_MAX_LEN);
        }
        return value;
    }

    private static String sanitizeDescription(String description) {
        String value = description == null ? "" : description.trim();
        if (value.length() > DESC_MAX_LEN) {
            value = value.substring(0, DESC_MAX_LEN);
        }
        return value;
    }
}
