package net.seep.odd.abilities.wizard.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.MathHelper;

import net.seep.odd.abilities.wizard.WizardCasting;
import net.seep.odd.abilities.wizard.WizardElement;
import net.seep.odd.item.ModItems;
import net.seep.odd.sound.ModSounds;

@Environment(EnvType.CLIENT)
public final class WizardChargeSoundClient {
    private WizardChargeSoundClient() {}

    private static ChargeSound sound;

    public static void tick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;

        boolean holdingStick = mc.player.getMainHandStack().isOf(ModItems.WALKING_STICK);
        boolean using = mc.player.isUsingItem() && holdingStick;

        if (!using) {
            if (sound != null) {
                sound.beginFadeOut();
                sound = null;
            }
            return;
        }

        if (sound == null) {
            sound = new ChargeSound(mc);
            mc.getSoundManager().play(sound);
        }

        WizardElement e = WizardClientState.getElementSafe();
        int needed = WizardCasting.chargeTicksFor(e);
        int used = mc.player.getItemUseTime();

        float t = (needed <= 1) ? 1f : (used / (float)needed);
        t = MathHelper.clamp(t, 0f, 1.25f);

        sound.setPitch(0.6f + t * 0.9f);
        sound.setVolume(0.6f + t * 0.35f);
    }

    private static final class ChargeSound extends MovingSoundInstance {
        private final MinecraftClient mc;
        private boolean fading = false;

        ChargeSound(MinecraftClient mc) {
            super(ModSounds.WIZARD_CHARGE, SoundCategory.PLAYERS, mc.player.getRandom());
            this.mc = mc;
            this.repeat = true;
            this.repeatDelay = 0;
            this.volume = 0.7f;
            this.pitch = 0.8f;
        }

        void setPitch(float p) { this.pitch = p; }
        void setVolume(float v) { this.volume = v; }

        void beginFadeOut() { fading = true; }

        @Override
        public void tick() {
            if (mc.player == null) {
                this.setDone();
                return;
            }

            this.x = (float)mc.player.getX();
            this.y = (float)mc.player.getY();
            this.z = (float)mc.player.getZ();

            if (fading) {
                this.volume *= 0.78f;
                if (this.volume < 0.02f) this.setDone();
            }
        }
    }
}
