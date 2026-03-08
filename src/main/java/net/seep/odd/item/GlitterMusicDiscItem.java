package net.seep.odd.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MusicDiscItem;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.MutableText;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class GlitterMusicDiscItem extends MusicDiscItem {
    public GlitterMusicDiscItem(int comparatorOutput, SoundEvent sound, Settings settings, int lengthInSeconds) {
        super(comparatorOutput, sound, settings, lengthInSeconds);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(
                Text.literal("")
                        .append(Text.literal("G").setStyle(Style.EMPTY.withColor(0xB8F1FF)))
                        .append(Text.literal("l").setStyle(Style.EMPTY.withColor(0xAEE7FF)))
                        .append(Text.literal("i").setStyle(Style.EMPTY.withColor(0xA8DBFF)))
                        .append(Text.literal("t").setStyle(Style.EMPTY.withColor(0xB6C8FF)))
                        .append(Text.literal("t").setStyle(Style.EMPTY.withColor(0xC5B2FF)))
                        .append(Text.literal("e").setStyle(Style.EMPTY.withColor(0xD09EFF)))
                        .append(Text.literal("r").setStyle(Style.EMPTY.withColor(0xDB8CFF)))
        );
    }
}