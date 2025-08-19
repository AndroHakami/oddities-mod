// net/seep/odd/item/ghost/client/GhostPullLoopSound.java
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
public final class GhostPullLoopSound extends MovingSoundInstance {
    private final PlayerEntity player;
    private final BooleanSupplier shouldKeepPlaying;

    public GhostPullLoopSound(PlayerEntity player, BooleanSupplier shouldKeepPlaying) {
        // ✅ pass a Random, not the SoundManager
        super(ModSounds.CREEPY_PULL, SoundCategory.PLAYERS, Random.create());

        this.player = player;
        this.shouldKeepPlaying = shouldKeepPlaying;

        this.repeat = true;      // loop
        this.repeatDelay = 0;
        this.volume = 1.0f;
        this.pitch = 1.0f;

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
