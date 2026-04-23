package net.seep.odd.quest.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.seep.odd.quest.QuestDefinition;
import net.seep.odd.quest.client.screen.LibrarianQuestScreen;

public final class QuestMusicController {
    private static String activeQuestId = "";
    private static String activeMusicId = "";
    private static SoundInstance currentSound;

    private QuestMusicController() {
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.getSoundManager() == null) {
                stop(client);
                return;
            }

            if (client.currentScreen instanceof LibrarianQuestScreen) {
                stop(client);
                return;
            }

            QuestClientState state = QuestClientState.INSTANCE;
            if (!state.hasActiveQuest() || !state.playMusic()) {
                stop(client);
                return;
            }

            QuestDefinition def = state.activeQuestDefinition();
            if (def == null || def.music == null || def.music.isBlank()) {
                stop(client);
                return;
            }

            final Identifier soundId;
            try {
                soundId = new Identifier(def.music);
            } catch (Exception ignored) {
                stop(client);
                return;
            }

            if (!Registries.SOUND_EVENT.containsId(soundId)) {
                stop(client);
                return;
            }

            if (!state.activeQuestId().equals(activeQuestId) || !soundId.toString().equals(activeMusicId)) {
                stop(client);
                activeQuestId = state.activeQuestId();
                activeMusicId = soundId.toString();
            }

            if (currentSound == null || !client.getSoundManager().isPlaying(currentSound)) {
                SoundEvent soundEvent = Registries.SOUND_EVENT.get(soundId);
                currentSound = PositionedSoundInstance.master(soundEvent, 1.0f, 1.0f);
                client.getSoundManager().play(currentSound);
            }
        });
    }

    private static void stop(MinecraftClient client) {
        activeQuestId = "";
        activeMusicId = "";
        if (currentSound != null) {
            client.getSoundManager().stop(currentSound);
            currentSound = null;
        }
    }
}
