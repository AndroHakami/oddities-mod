package net.seep.odd.device.bank;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;

public final class DabloonBankNetworking {
    private DabloonBankNetworking() {}

    public static final Identifier C2S_REQUEST_SYNC = new Identifier(Oddities.MOD_ID, "bank_request_sync");
    public static final Identifier S2C_SYNC         = new Identifier(Oddities.MOD_ID, "bank_sync");
    public static final Identifier C2S_DEPOSIT      = new Identifier(Oddities.MOD_ID, "bank_deposit");
    public static final Identifier C2S_WITHDRAW     = new Identifier(Oddities.MOD_ID, "bank_withdraw");

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(DabloonBankManager::load);
        ServerLifecycleEvents.SERVER_STOPPING.register(DabloonBankManager::save);

        ServerPlayNetworking.registerGlobalReceiver(C2S_REQUEST_SYNC, (server, player, handler, buf, sender) ->
                server.execute(() -> sendSync(player)));

        ServerPlayNetworking.registerGlobalReceiver(C2S_DEPOSIT, (server, player, handler, buf, sender) -> {
            int amount = buf.readVarInt();
            server.execute(() -> {
                DabloonBankManager.BankResult result = DabloonBankManager.deposit(player, amount);
                if (!result.ok()) {
                    player.sendMessage(Text.literal(result.error()), true);
                } else {
                    player.sendMessage(Text.literal("Deposited " + result.amount() + " dabloons."), true);
                }
                sendSync(player);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(C2S_WITHDRAW, (server, player, handler, buf, sender) -> {
            int amount = buf.readVarInt();
            server.execute(() -> {
                DabloonBankManager.BankResult result = DabloonBankManager.withdraw(player, amount);
                if (!result.ok()) {
                    player.sendMessage(Text.literal(result.error()), true);
                } else {
                    player.sendMessage(Text.literal("Withdrew " + result.amount() + " dabloons."), true);
                }
                sendSync(player);
            });
        });
    }

    public static void sendSync(ServerPlayerEntity player) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(DabloonBankManager.getBalance(player.getUuid()));
        buf.writeBoolean(DabloonBankManager.canDepositAtCurrentLocation(player));
        ServerPlayNetworking.send(player, S2C_SYNC, buf);
    }
}