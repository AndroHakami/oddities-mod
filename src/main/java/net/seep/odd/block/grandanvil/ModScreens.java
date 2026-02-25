// src/main/java/net/seep/odd/block/grandanvil/ModScreens.java
package net.seep.odd.block.grandanvil;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;

// ✅ new combiner handler
import net.seep.odd.block.combiner.CombinerScreenHandler;

public final class ModScreens {
    private ModScreens(){}

    public static ExtendedScreenHandlerType<GrandAnvilScreenHandler> GRAND_ANVIL;
    public static ExtendedScreenHandlerType<CombinerScreenHandler> COMBINER;

    public static void register() {
        // Old (kept for backwards compatibility)
        GRAND_ANVIL = Registry.register(
                Registries.SCREEN_HANDLER,
                new Identifier(Oddities.MOD_ID, "grand_anvil"),
                new ExtendedScreenHandlerType<>(
                        (syncId, playerInv, buf) -> new GrandAnvilScreenHandler(syncId, playerInv, buf.readBlockPos())
                )
        );

        // ✅ New Combiner screen
        COMBINER = Registry.register(
                Registries.SCREEN_HANDLER,
                new Identifier(Oddities.MOD_ID, "combiner"),
                new ExtendedScreenHandlerType<>(
                        (syncId, playerInv, buf) -> new CombinerScreenHandler(syncId, playerInv, buf.readBlockPos())
                )
        );
    }
}