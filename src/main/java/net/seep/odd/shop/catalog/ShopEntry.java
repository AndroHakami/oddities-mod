package net.seep.odd.shop.catalog;

import com.google.gson.annotations.SerializedName;

public final class ShopEntry {
    public enum PreviewType { @SerializedName("item") ITEM, @SerializedName("entity") ENTITY }

    public String id;
    public String displayName;
    public int price;

    // what you actually receive on buy
    public String giveItemId = "minecraft:stone";
    public int giveCount = 1;

    // preview info
    public PreviewType previewType = PreviewType.ITEM;

    // for previewType=item
    public String previewItemId;

    // for previewType=entity
    public String previewEntityType; // e.g. "minecraft:cat"

    public ShopEntry() {}
}
