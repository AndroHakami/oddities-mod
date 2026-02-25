package net.seep.odd.abilities.artificer.mixer.brew.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;

import net.seep.odd.sound.ModSounds;

/**
 * A non-looping sound instance that follows the player while it plays.
 * (If your sound is ~3s long, it will naturally cover the whole effect without looping.)
 */
@Environment(EnvType.CLIENT)
public final class RefractionSoundInstance extends MovingSoundInstance {

    private final PlayerEntity player;
    private int age = 0;
    private final int maxTicks;

    public RefractionSoundInstance(PlayerEntity player, int maxTicks) {
        super(ModSounds.REFRACTION, SoundCategory.PLAYERS, SoundInstance.createRandom());
        this.player = player;
        this.maxTicks = Math.max(1, maxTicks);

        this.repeat = false;
        this.repeatDelay = 0;
        this.volume = 1.0f;
        this.pitch = 1.0f;

        updatePos();
    }

    @Override
    public void tick() {
        if (player == null || !player.isAlive() || MinecraftClient.getInstance().player != player) {
            setDone();
            return;
        }

        updatePos();

        age++;
        if (age > maxTicks) setDone();
    }

    private void updatePos() {
        this.x = player.getX();
        this.y = player.getY();
        this.z = player.getZ();
    }

    /** Convenience: start one-shot refraction sound that follows the local player. */
    public static void playOnce(int durationTicks) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;

        mc.getSoundManager().play(new RefractionSoundInstance(mc.player, durationTicks));
    }
}
