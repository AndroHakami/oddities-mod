package net.seep.odd.abilities.overdrive;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class OverdriveNet {
    private OverdriveNet() {}

    // C → S
    public static final Identifier TOGGLE_ENERGIZED = new Identifier("odd","ov_toggle_energized");
    public static final Identifier RELAY_ON         = new Identifier("odd","ov_relay_on");
    public static final Identifier RELAY_OFF        = new Identifier("odd","ov_relay_off");
    public static final Identifier START_PUNCH      = new Identifier("odd","ov_start_punch");
    public static final Identifier RELEASE_PUNCH    = new Identifier("odd","ov_release_punch");
    public static final Identifier ACTIVATE_OD      = new Identifier("odd","ov_overdrive");

    // S → C (HUD)
    public static final Identifier HUD_SYNC         = new Identifier("odd","ov_hud");
    public static final Identifier OVERDRIVE_ANIM = new Identifier("odd", "overdrive_anim");
    public static final Identifier OV_HUD = new Identifier("odd","ov_hud");

    /* ---------------- client sends ---------------- */

    public static void sendToggleEnergized() { ClientPlayNetworking.send(TOGGLE_ENERGIZED, PacketByteBufs.create()); }
    public static void sendRelay(boolean on)  { ClientPlayNetworking.send(on ? RELAY_ON : RELAY_OFF, PacketByteBufs.create()); }
    public static void sendStartPunch()       { ClientPlayNetworking.send(START_PUNCH, PacketByteBufs.create()); }
    public static void sendReleasePunch()     { ClientPlayNetworking.send(RELEASE_PUNCH, PacketByteBufs.create()); }
    public static void sendOverdrive()        { ClientPlayNetworking.send(ACTIVATE_OD, PacketByteBufs.create()); }

    /* ---------------- server registers ---------------- */

    public static void initServer() {
        ServerPlayNetworking.registerGlobalReceiver(TOGGLE_ENERGIZED, (server, player, h, buf, rs) ->
                server.execute(() -> OverdriveSystem.toggleEnergized(player)));
        ServerPlayNetworking.registerGlobalReceiver(RELAY_ON, (s, p, h, b, rs) -> s.execute(() -> OverdriveSystem.setRelay(p, true)));
        ServerPlayNetworking.registerGlobalReceiver(RELAY_OFF,(s, p, h, b, rs) -> s.execute(() -> OverdriveSystem.setRelay(p, false)));
        ServerPlayNetworking.registerGlobalReceiver(START_PUNCH, (s, p, h, b, rs) -> s.execute(() -> OverdriveSystem.startPunch(p)));
        ServerPlayNetworking.registerGlobalReceiver(RELEASE_PUNCH,(s, p, h, b, rs) -> s.execute(() -> OverdriveSystem.releasePunch(p)));
        ServerPlayNetworking.registerGlobalReceiver(ACTIVATE_OD,(s, p, h, b, rs) -> s.execute(() -> OverdriveSystem.tryOverdrive(p)));
    }
    // ANIMATION
    public static void sendOverdriveAnim(ServerPlayerEntity player, boolean active) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(active);
        ServerPlayNetworking.send(player, OVERDRIVE_ANIM, buf);
    }

    /* ---------------- server → client HUD ---------------- */

    public static void sendHud(ServerPlayerEntity p, float energy, int modeOrdinal, int ticksLeft) {
        var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeFloat(energy);
        buf.writeByte(modeOrdinal & 0xFF);   // 0..255, we only use 0..2
        buf.writeVarInt(ticksLeft);          // 0 if not in Overdrive
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, OV_HUD, buf);
    }

    public static void initClient() {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                OV_HUD, (client, handler, buf, rs) -> {
                    float energy  = buf.readFloat();
                    int modeOrd   = buf.readUnsignedByte();
                    int odTicks   = buf.readVarInt();

                    client.execute(() -> {
                        // update your Overdrive HUD state
                        OverdriveHudOverlay.clientHudUpdate(energy, modeOrd, odTicks);
                        // also tell the CPM bridge watcher
                        net.seep.odd.abilities.overdrive.client.OverdriveClientState.setModeOrdinal(modeOrd);
                    });
                }
        );
    }
}
