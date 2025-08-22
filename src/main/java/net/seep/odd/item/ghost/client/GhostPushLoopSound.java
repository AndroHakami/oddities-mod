// net/seep/odd/item/ghost/client/GhostPushLoopSound.java
package net.seep.odd.item.ghost.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.random.Random;

import java.util.function.BooleanSupplier;

import net.seep.odd.sound.ModSounds;

@Environment(EnvType.CLIENT)
public final class GhostPushLoopSound extends MovingSoundInstance {
    private final PlayerEntity player;
    private final BooleanSupplier shouldKeepPlaying;

    /**
     * @param player The local player the sound should follow
     * @param shouldKeepPlaying Supplier that returns true while LMB push is active
     */
    public GhostPushLoopSound(PlayerEntity player, BooleanSupplier shouldKeepPlaying) {
        // NOTE: Use your push sound here. If your ModSounds name differs, swap it.
        // e.g. ModSounds.GHOST_PUSH_LOOP or ModSounds.CREEPY_PUSH
        super(ModSounds.CREEPY_PUSH, SoundCategory.PLAYERS, Random.create());

        this.player = player;
        this.shouldKeepPlaying = shouldKeepPlaying;

        this.repeat = true;      // loop while held
        this.repeatDelay = 0;
        this.volume = 1.0f;
        this.pitch  = 1.0f;

        this.x = player.getX();
        this.y = player.getY();
        this.z = player.getZ();
    }

    @Override
    public void tick() {
        var mc = MinecraftClient.getInstance();
        if (mc == null || player == null || player.isRemoved() || mc.player != player || !shouldKeepPlaying.getAsBoolean()) {
            this.setDone();
            return;
        }

        // follow player
        this.x = player.getX();
        this.y = player.getY();
        this.z = player.getZ();
    }
}
