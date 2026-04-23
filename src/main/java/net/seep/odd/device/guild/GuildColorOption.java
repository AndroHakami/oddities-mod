package net.seep.odd.device.guild;

import net.minecraft.util.Formatting;

public enum GuildColorOption {
    BLACK("black", "Black", 0xFF4A4A52, Formatting.BLACK),
    NAVY("navy", "Navy", 0xFF4B63B8, Formatting.DARK_BLUE),
    FOREST("forest", "Forest", 0xFF4FA86E, Formatting.DARK_GREEN),
    TEAL("teal", "Teal", 0xFF53D6D0, Formatting.DARK_AQUA),
    CRIMSON("crimson", "Crimson", 0xFFD84B63, Formatting.DARK_RED),
    VIOLET("violet", "Violet", 0xFF9F69D9, Formatting.DARK_PURPLE),
    GOLD("gold", "Gold", 0xFFFFC145, Formatting.GOLD),
    SLATE("slate", "Slate", 0xFF9AA5B8, Formatting.GRAY),
    CHARCOAL("charcoal", "Charcoal", 0xFF6E7689, Formatting.DARK_GRAY),
    BLUE("blue", "Blue", 0xFF6FA8FF, Formatting.BLUE),
    MINT("mint", "Mint", 0xFF95F9C3, Formatting.GREEN),
    AZURE("azure", "Azure", 0xFF79B8FF, Formatting.AQUA),
    ROSE("rose", "Rose", 0xFFFF8FA3, Formatting.RED),
    LILAC("lilac", "Lilac", 0xFFD5B3FF, Formatting.LIGHT_PURPLE),
    AMBER("amber", "Amber", 0xFFFFD166, Formatting.YELLOW);

    private final String id;
    private final String label;
    private final int color;
    private final Formatting formatting;

    GuildColorOption(String id, String label, int color, Formatting formatting) {
        this.id = id;
        this.label = label;
        this.color = color;
        this.formatting = formatting;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public int color() {
        return color;
    }

    public Formatting formatting() {
        return formatting;
    }

    public GuildColorOption next() {
        GuildColorOption[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }

    public GuildColorOption previous() {
        GuildColorOption[] values = values();
        return values[(this.ordinal() - 1 + values.length) % values.length];
    }

    public static GuildColorOption byId(String id) {
        if (id != null) {
            for (GuildColorOption option : values()) {
                if (option.id.equalsIgnoreCase(id)) {
                    return option;
                }
            }
        }
        return AZURE;
    }
}
