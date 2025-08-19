package net.seep.odd.block.grandanvil;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;

public final class ModScreens {
    private ModScreens(){}

    public static ExtendedScreenHandlerType<GrandAnvilScreenHandler> GRAND_ANVIL;

    public static void register() {
        GRAND_ANVIL = Registry.register(
                Registries.SCREEN_HANDLER,
                new Identifier(Oddities.MOD_ID, "grand_anvil"),
                new net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType<>(
                        (syncId, playerInv, buf) -> new GrandAnvilScreenHandler(syncId, playerInv, buf.readBlockPos())
                )
        );
    }
}
