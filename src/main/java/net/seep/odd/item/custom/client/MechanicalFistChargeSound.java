package net.seep.odd.item.custom.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.seep.odd.item.custom.MechanicalFistItem;
import net.seep.odd.sound.ModSounds;

@Environment(EnvType.CLIENT)
public final class MechanicalFistChargeSound extends MovingSoundInstance {
    private static boolean inited = false;
    private static MechanicalFistChargeSound active;

    private final ClientPlayerEntity player;

    private MechanicalFistChargeSound(ClientPlayerEntity player) {
        super(ModSounds.FIST_PUNCH_STRONG_CHARGE, SoundCategory.PLAYERS, SoundInstance.createRandom());
        this.player = player;
        this.repeat = true;
        this.repeatDelay = 0;
        this.volume = 0.92F;
        this.pitch = 1.0F;
        this.relative = false;
        syncToPlayer();
    }

    public static void init() {
        if (inited) return;
        inited = true;

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client == null || client.player == null || client.world == null) {
                stopActive();
                return;
            }

            tickController(client);
        });
    }

    private static void tickController(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        boolean charging = player != null
                && player.isUsingItem()
                && player.getActiveItem().getItem() instanceof MechanicalFistItem;

        if (!charging) {
            stopActive();
            return;
        }

        if (active == null) {
            active = new MechanicalFistChargeSound(player);
            client.getSoundManager().play(active);
        }
    }

    private static void stopActive() {
        if (active != null) {
            active.setDone();
            active = null;
        }
    }

    @Override
    public void tick() {
        if (player == null || player.isRemoved() || !player.isUsingItem() || !(player.getActiveItem().getItem() instanceof MechanicalFistItem)) {
            setDone();
            if (active == this) {
                active = null;
            }
            return;
        }

        syncToPlayer();
    }

    private void syncToPlayer() {
        this.x = player.getX();
        this.y = player.getEyeY() - 0.15D;
        this.z = player.getZ();
    }
}
