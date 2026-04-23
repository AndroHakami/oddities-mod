package net.seep.odd.shop.catalog;

import com.google.gson.annotations.SerializedName;

public final class ShopEntry {

    public enum PreviewType {
        @SerializedName("item") ITEM,
        @SerializedName("entity") ENTITY,
        @SerializedName("armour") ARMOUR
    }

    public enum PreviewArmourSlot {
        @SerializedName("auto") AUTO,
        @SerializedName("head") HEAD,
        @SerializedName("chest") CHEST,
        @SerializedName("legs") LEGS,
        @SerializedName("feet") FEET
    }

    public enum Category {
        @SerializedName("weapons") WEAPONS,
        @SerializedName("pets") PETS,
        @SerializedName("styles") STYLES,
        @SerializedName("misc") MISC
    }

    public enum GrantType {
        @SerializedName("item") ITEM,
        @SerializedName("command") COMMAND
    }

    public String id;
    public String displayName;
    public String description = "";
    public int price;

    public Category category = Category.MISC;
    public int sortOrder = 0;
    public boolean pet = false;

    public GrantType grantType = GrantType.ITEM;
    public String giveItemId = "minecraft:stone";
    public int giveCount = 1;
    public String grantCommand = "";

    public PreviewType previewType = PreviewType.ITEM;
    public String previewItemId = "";
    public String previewEntityType = "";
    public PreviewArmourSlot previewArmourSlot = PreviewArmourSlot.AUTO;

    public ShopEntry() {}
}
