package net.seep.odd.expeditions.rottenroots.boggy;

import net.fabricmc.fabric.api.object.builder.v1.block.type.BlockSetTypeBuilder;
import net.fabricmc.fabric.api.object.builder.v1.block.type.WoodTypeBuilder;
import net.minecraft.block.BlockSetType;
import net.minecraft.block.WoodType;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;

public final class BoggyWoodTypes {
    private BoggyWoodTypes() {}

    public static final Identifier ID = new Identifier(Oddities.MOD_ID, "boggy");

    // ✅ REGISTER (not build) so it’s globally known
    public static final BlockSetType BLOCK_SET_TYPE =
            BlockSetTypeBuilder.copyOf(BlockSetType.OAK).register(ID);

    public static final WoodType WOOD_TYPE =
            WoodTypeBuilder.copyOf(WoodType.OAK).register(ID, BLOCK_SET_TYPE);
}