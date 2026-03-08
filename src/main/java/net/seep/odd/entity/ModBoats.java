package net.seep.odd.entity;

import com.terraformersmc.terraform.boat.api.TerraformBoatType;
import com.terraformersmc.terraform.boat.api.TerraformBoatTypeRegistry;

import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;

import net.seep.odd.Oddities;
import net.seep.odd.block.ModBlocks;
import net.seep.odd.item.ModItems;

public final class ModBoats {
    private ModBoats() {}

    // NOTE: this is the "base" boat id used by Terraform for BOTH boat + chest boat rendering
    public static final Identifier BOGGY_BOAT_ID = new Identifier(Oddities.MOD_ID, "boggy_boat");
    public static final Identifier BOGGY_CHEST_BOAT_ID = new Identifier(Oddities.MOD_ID, "boggy_chest_boat");

    public static final RegistryKey<TerraformBoatType> BOGGY_BOAT_KEY =
            TerraformBoatTypeRegistry.createKey(BOGGY_BOAT_ID);

    public static void registerBoats() {
        TerraformBoatType boggy = new TerraformBoatType.Builder()
                .item(ModItems.BOGGY_BOAT)                 // your boat item
                .chestItem(ModItems.BOGGY_CHEST_BOAT)      // your chest boat item
                .planks(ModBlocks.BOGGY_PLANKS.asItem())   // your planks item
                .build();

        Registry.register(TerraformBoatTypeRegistry.INSTANCE, BOGGY_BOAT_KEY, boggy);
    }
}