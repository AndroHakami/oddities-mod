package net.seep.odd.quest.client;

public final class ModQuestsClient {
    private ModQuestsClient() {
    }

    public static void init() {
        QuestClientNetworking.init();
        QuestKeybinds.init();
        QuestHudOverlay.init();
        QuestAreaMarkerFx.init();
        StarRideTrailFx.init();
        QuestMusicController.init();
        ScaredRascalQuestClientBootstrap.init();
    }
}
