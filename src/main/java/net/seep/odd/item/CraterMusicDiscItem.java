package net.seep.odd.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MusicDiscItem;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class CraterMusicDiscItem extends MusicDiscItem {
    public CraterMusicDiscItem(int comparatorOutput, SoundEvent sound, Settings settings, int lengthInSeconds) {
        super(comparatorOutput, sound, settings, lengthInSeconds);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(
                Text.literal("")
                        .append(Text.literal("T").setStyle(Style.EMPTY.withColor(0xCC5500))) // Deep Orange
                        .append(Text.literal("e").setStyle(Style.EMPTY.withColor(0xD97700)))
                        .append(Text.literal("m").setStyle(Style.EMPTY.withColor(0xE69900)))
                        .append(Text.literal("p").setStyle(Style.EMPTY.withColor(0xF2BB00)))
                        .append(Text.literal("l").setStyle(Style.EMPTY.withColor(0xFFDD00))) // Bright Gold
                        .append(Text.literal("e").setStyle(Style.EMPTY.withColor(0xFFE633)))
                        .append(Text.literal(" ").setStyle(Style.EMPTY))
                        .append(Text.literal("o").setStyle(Style.EMPTY.withColor(0xFFDD00)))
                        .append(Text.literal("f").setStyle(Style.EMPTY.withColor(0xF2BB00)))
                        .append(Text.literal(" ").setStyle(Style.EMPTY))
                        .append(Text.literal("t").setStyle(Style.EMPTY.withColor(0xE69900)))
                        .append(Text.literal("h").setStyle(Style.EMPTY.withColor(0xD97700)))
                        .append(Text.literal("e").setStyle(Style.EMPTY.withColor(0xCC5500)))
                        .append(Text.literal(" ").setStyle(Style.EMPTY))
                        .append(Text.literal("C").setStyle(Style.EMPTY.withColor(0xCC5500)))
                        .append(Text.literal("r").setStyle(Style.EMPTY.withColor(0xD97700)))
                        .append(Text.literal("a").setStyle(Style.EMPTY.withColor(0xE69900)))
                        .append(Text.literal("t").setStyle(Style.EMPTY.withColor(0xF2BB00)))
                        .append(Text.literal("e").setStyle(Style.EMPTY.withColor(0xFFDD00)))
                        .append(Text.literal("r").setStyle(Style.EMPTY.withColor(0xFFE633)))
        );
    }
}