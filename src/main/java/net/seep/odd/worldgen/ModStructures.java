package net.seep.odd.worldgen;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.structure.StructurePieceType;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.structure.StructureType;
import net.seep.odd.Oddities;
import net.seep.odd.worldgen.structure.ShroomVillagePiece;
import net.seep.odd.worldgen.structure.ShroomVillageStructure;

public final class ModStructures {
    private ModStructures() {}

    private static Identifier id(String path) {
        return new Identifier(Oddities.MOD_ID, path);
    }

    // Structure TYPE (used by data json: "type": "odd:shroom_village")
    public static final StructureType<ShroomVillageStructure> SHROOM_VILLAGE_TYPE =
            Registry.register(Registries.STRUCTURE_TYPE, id("shroom_village"), () -> ShroomVillageStructure.CODEC);

    // Piece type (required so the structure can save/load)
    public static final StructurePieceType SHROOM_VILLAGE_PIECE =
            Registry.register(Registries.STRUCTURE_PIECE, id("shroom_village"), ShroomVillagePiece::new);

    public static void init() {
        // class-loads the statics
    }
}