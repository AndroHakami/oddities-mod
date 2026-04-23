package net.seep.odd.quest.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screen.Screen;
import net.seep.odd.quest.QuestNetworking;
import net.seep.odd.quest.client.screen.LibrarianQuestScreen;
import net.seep.odd.quest.client.screen.LoreQuizScreen;
import net.seep.odd.quest.client.screen.ScaredRascalDialogScreen;

public final class QuestClientNetworking {
    private QuestClientNetworking() {
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(QuestNetworking.S2C_SYNC, (client, handler, buf, responseSender) -> {
            QuestNetworking.DecodedSync sync = QuestNetworking.decodeClientSync(buf);

            client.execute(() -> {
                QuestClientState.INSTANCE.apply(sync);

                if (sync.openScreen) {
                    client.setScreen(new LibrarianQuestScreen(sync.librarianEntityId));
                    return;
                }

                if (client.currentScreen instanceof LoreQuizScreen loreQuizScreen) {
                    boolean sameQuestStillActive = sync.hasActiveQuest && sync.activeQuestId.equals(loreQuizScreen.quizQuestId());
                    boolean shouldReturnToLibrarian =
                            sync.readyToClaim
                                    || !sameQuestStillActive
                                    || !sync.hasActiveQuest;

                    if (shouldReturnToLibrarian) {
                        client.setScreen(new LibrarianQuestScreen(sync.librarianEntityId));
                    }
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(QuestNetworking.S2C_OPEN_SCARED_RASCAL_DIALOG, (client, handler, buf, responseSender) -> {
            int rascalEntityId = buf.readInt();
            String title = buf.readString();
            String line = buf.readString();
            String buttonText = buf.readString();

            client.execute(() -> client.setScreen(new ScaredRascalDialogScreen(rascalEntityId, title, line, buttonText)));
        });

        ClientPlayNetworking.registerGlobalReceiver(QuestNetworking.S2C_OPEN_LORE_QUIZ, (client, handler, buf, responseSender) -> {
            int librarianEntityId = buf.readInt();
            String questId = buf.readString();
            String volumeId = buf.readString();
            String volumeTitle = buf.readString();
            String question = buf.readString();

            int answerCount = buf.readInt();
            String[] answers = new String[answerCount];
            for (int i = 0; i < answerCount; i++) {
                answers[i] = buf.readString();
            }

            client.execute(() -> {
                Screen parent = client.currentScreen instanceof LibrarianQuestScreen
                        ? client.currentScreen
                        : new LibrarianQuestScreen(librarianEntityId);

                client.setScreen(new LoreQuizScreen(
                        parent,
                        librarianEntityId,
                        questId,
                        volumeId,
                        volumeTitle,
                        question,
                        answers
                ));
            });
        });
    }
}
