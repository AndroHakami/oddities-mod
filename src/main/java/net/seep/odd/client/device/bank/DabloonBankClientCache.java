package net.seep.odd.client.device.bank;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.seep.odd.device.bank.DabloonBankNetworking;

@Environment(EnvType.CLIENT)
public final class DabloonBankClientCache {
    private DabloonBankClientCache() {}

    private static int balance = 0;
    private static boolean depositAllowed = false;

    public static void initClient() {
        ClientPlayNetworking.registerGlobalReceiver(DabloonBankNetworking.S2C_SYNC, (client, handler, buf, sender) -> {
            int incomingBalance = buf.readVarInt();
            boolean incomingDepositAllowed = buf.readBoolean();

            client.execute(() -> {
                balance = incomingBalance;
                depositAllowed = incomingDepositAllowed;
            });
        });
    }

    public static int balance() {
        return balance;
    }

    public static boolean depositAllowed() {
        return depositAllowed;
    }
}