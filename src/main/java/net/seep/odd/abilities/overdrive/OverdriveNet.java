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

    // S → C
    public static final Identifier OV_HUD           = new Identifier("odd","ov_hud");
    public static final Identifier OVERDRIVE_ANIM   = new Identifier("odd","overdrive_anim"); // true=start run, false=stop run

    /* ---------------- client sends ---------------- */
    public static void sendToggleEnergized() { ClientPlayNetworking.send(TOGGLE_ENERGIZED, PacketByteBufs.create()); }
    public static void sendRelay(boolean on){ ClientPlayNetworking.send(on ? RELAY_ON : RELAY_OFF, PacketByteBufs.create()); }
    public static void sendStartPunch() {
        // ✔ gate by current power on the client
        if (!"overdrive".equals(net.seep.odd.abilities.client.ClientPowerHolder.get())) return;
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(START_PUNCH, PacketByteBufs.create());
        net.seep.odd.abilities.overdrive.client.OverdriveCpmBridge.onLocalChargeStart();
    }
    public static void sendReleasePunch() {
        if (!"overdrive".equals(net.seep.odd.abilities.client.ClientPowerHolder.get())) return;
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(RELEASE_PUNCH, PacketByteBufs.create());
        net.seep.odd.abilities.overdrive.client.OverdriveCpmBridge.onLocalPunch();
    }
    public static void sendOverdrive()       { ClientPlayNetworking.send(ACTIVATE_OD, PacketByteBufs.create()); }

    /* ---------------- server registers ---------------- */
    public static void initServer() {
        ServerPlayNetworking.registerGlobalReceiver(TOGGLE_ENERGIZED, (s, p, h, b, rs) -> s.execute(() -> OverdriveSystem.toggleEnergized(p)));
        ServerPlayNetworking.registerGlobalReceiver(RELAY_ON,        (s, p, h, b, rs) -> s.execute(() -> OverdriveSystem.setRelay(p, true)));
        ServerPlayNetworking.registerGlobalReceiver(RELAY_OFF,       (s, p, h, b, rs) -> s.execute(() -> OverdriveSystem.setRelay(p, false)));
        ServerPlayNetworking.registerGlobalReceiver(START_PUNCH,     (s, p, h, b, rs) -> s.execute(() -> OverdriveSystem.startPunch(p)));
        ServerPlayNetworking.registerGlobalReceiver(RELEASE_PUNCH,   (s, p, h, b, rs) -> s.execute(() -> OverdriveSystem.releasePunch(p)));
        ServerPlayNetworking.registerGlobalReceiver(ACTIVATE_OD,     (s, p, h, b, rs) -> s.execute(() -> OverdriveSystem.tryOverdrive(p)));
    }

    /* ---------------- S → C utility ---------------- */
    public static void sendOverdriveAnim(ServerPlayerEntity player, boolean active) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(active);
        ServerPlayNetworking.send(player, OVERDRIVE_ANIM, buf);
    }

    public static void sendHud(ServerPlayerEntity p, float energy, int modeOrdinal, int ticksLeft) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeFloat(energy);
        buf.writeByte(modeOrdinal & 0xFF);
        buf.writeVarInt(ticksLeft);
        ServerPlayNetworking.send(p, OV_HUD, buf);
    }


    /* ---------------- client registers ---------------- */
    public static void initClient() {
        // HUD sync
        ClientPlayNetworking.registerGlobalReceiver(OV_HUD, (client, handler, buf, rs) -> {
            float energy  = buf.readFloat();
            int modeOrd   = buf.readUnsignedByte();
            int odTicks   = buf.readVarInt();
            client.execute(() -> {
                OverdriveHudOverlay.clientHudUpdate(energy, modeOrd, odTicks);
                // (Optional) If you keep a client state mirror elsewhere, update it here too.
            });
        });

        // Overdrive run animation toggle (server-authoritative)
        ClientPlayNetworking.registerGlobalReceiver(OVERDRIVE_ANIM, (client, handler, buf, rs) -> {
            boolean active = buf.readBoolean();
            client.execute(() -> {
                if (active) {
                    net.seep.odd.abilities.overdrive.client.CpmHooks.play("overdrive_run");
                } else {
                    net.seep.odd.abilities.overdrive.client.CpmHooks.stop("overdrive_run");
                }
            });
        });
    }
}
