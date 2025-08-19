package net.seep.odd.item;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.block.ModBlocks; // make sure CRAPPY_BLOCK is defined here

public class ModItemGroups {


    public static void registerItemGroups() {
        Oddities.LOGGER.info("Registering Mod Item Groups For " + Oddities.MOD_ID);
    }
}
