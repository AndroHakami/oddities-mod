package net.seep.odd.item.custom;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TooltipItem extends Item {
    private final Formatting color;
    private final boolean italic;
    private final int maxLines;

    public TooltipItem(Settings settings) {
        this(settings, Formatting.DARK_GRAY, true, 4);
    }

    public TooltipItem(Settings settings, Formatting color, boolean italic, int maxLines) {
        super(settings);
        this.color = color;
        this.italic = italic;
        this.maxLines = Math.max(1, maxLines);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        Identifier id = Registries.ITEM.getId(stack.getItem());
        if (id == null) return;

        // item.modid.name.tooltip, tooltip2, tooltip3...
        for (int i = 1; i <= maxLines; i++) {
            String key = "item." + id.getNamespace() + "." + id.getPath() + ".tooltip" + (i == 1 ? "" : i);

            // Only add if translation exists (prevents showing raw key)
            if (!Text.translatable(key).getString().equals(key)) {
                Text t = Text.translatable(key).formatted(color);
                if (italic) t = t.copy().formatted(Formatting.ITALIC);
                tooltip.add(t);
            } else {
                // stop once the first missing line is reached
                if (i == 1) return;
                break;
            }
        }
    }
}
