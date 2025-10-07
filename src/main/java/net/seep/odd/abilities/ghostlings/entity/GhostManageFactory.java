package net.seep.odd.abilities.ghostlings.entity;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.seep.odd.abilities.ghostlings.screen.GhostManageScreenHandler;

public class GhostManageFactory implements ExtendedScreenHandlerFactory {
    private final GhostlingEntity ghost;
    public GhostManageFactory(GhostlingEntity ghost) { this.ghost = ghost; }

    @Override
    public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf buf) {
        buf.writeVarInt(ghost.getId());                    // entity id
        buf.writeEnumConstant(ghost.getJob());             // <-- send job to client
    }

    @Override public Text getDisplayName() { return Text.of("Manage Ghostling"); }

    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity player) {
        return new GhostManageScreenHandler(syncId, inv, ghost.getId(), ghost.getJob());
    }
}
