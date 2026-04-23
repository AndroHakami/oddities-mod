package net.seep.odd.quest.types;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.seep.odd.lore.AtheneumLoreBooks;
import net.seep.odd.quest.ActiveQuestState;
import net.seep.odd.quest.PlayerQuestProfile;
import net.seep.odd.quest.QuestDefinition;
import net.seep.odd.quest.QuestManager;
import net.seep.odd.quest.QuestRegistry;

public final class BookQuizQuestLogic {
    private BookQuizQuestLogic() {}

    public static ActiveQuestState create(ServerPlayerEntity player, String questId) {
        return ActiveQuestState.forLoreQuiz(questId, AtheneumLoreBooks.randomId(player.getRandom()));
    }

    public static boolean hasRequiredBook(ServerPlayerEntity player, ActiveQuestState active) {
        if (active == null || active.requestedLoreId == null || active.requestedLoreId.isBlank()) return false;

        for (int i = 0; i < player.getInventory().size(); i++) {
            if (AtheneumLoreBooks.matchesRequested(player.getInventory().getStack(i), active.requestedLoreId)) {
                return true;
            }
        }
        return false;
    }

    private static boolean readSatisfied(ActiveQuestState active, QuestDefinition def) {
        if (active == null) return false;
        if (active.requiredBookRead) return true;
        if (def == null) return active.progress > 0;
        return active.progress >= Math.max(1, def.goal());
    }

    public static String buildHint(ServerPlayerEntity player, ActiveQuestState active) {
        String title = AtheneumLoreBooks.titleOf(active.requestedLoreId);
        QuestDefinition def = QuestRegistry.get(active.questId);

        if (!hasRequiredBook(player, active)) {
            return "Find: " + title + " in a starry bookshelf";
        }
        if (!readSatisfied(active, def)) {
            return "Read " + title + " before returning to the librarian";
        }
        return "Return to the librarian for the quiz";
    }

    public static boolean readyForQuiz(ServerPlayerEntity player, ActiveQuestState active) {
        if (active == null) return false;
        QuestDefinition def = QuestRegistry.get(active.questId);
        return hasRequiredBook(player, active) && readSatisfied(active, def);
    }

    public static void markRead(ServerPlayerEntity player, ActiveQuestState active) {
        if (active == null) return;

        QuestDefinition def = QuestRegistry.get(active.questId);
        int goal = def == null ? 1 : Math.max(1, def.goal());

        if (active.requiredBookRead && active.progress >= goal) return;

        active.requiredBookRead = true;
        active.progress = Math.max(active.progress, goal);

        QuestManager.markDirty(player);
        QuestManager.refreshBossBar(player);
        QuestManager.syncQuest(player);

        player.sendMessage(
                Text.literal("You studied " + AtheneumLoreBooks.titleOf(active.requestedLoreId) + ". Return to the librarian."),
                true
        );
    }

    public static boolean consumeRequestedBook(ServerPlayerEntity player, ActiveQuestState active) {
        if (active == null || active.requestedLoreId == null || active.requestedLoreId.isBlank()) return false;

        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (AtheneumLoreBooks.matchesRequested(stack, active.requestedLoreId)) {
                stack.decrement(1);
                return true;
            }
        }
        return false;
    }

    public static void submitAnswer(ServerPlayerEntity player, int librarianEntityId, String questId, int selectedAnswer) {
        PlayerQuestProfile profile = QuestManager.profile(player);
        ActiveQuestState active = profile.activeQuest;
        if (active == null || !questId.equals(active.questId)) return;
        if (!QuestManager.validateLibrarianForQuiz(player, librarianEntityId)) return;

        QuestDefinition def = QuestRegistry.get(active.questId);
        AtheneumLoreBooks.Volume volume = AtheneumLoreBooks.get(active.requestedLoreId);
        if (def == null || volume == null) return;

        if (!hasRequiredBook(player, active)) {
            player.sendMessage(Text.literal("Bring the requested volume back to the librarian first."), true);
            return;
        }

        if (!readSatisfied(active, def)) {
            player.sendMessage(Text.literal("Read the requested volume first."), true);
            return;
        }

        if (selectedAnswer < 0 || selectedAnswer >= volume.answers().length) {
            return;
        }

        if (selectedAnswer == volume.correctAnswer()) {
            consumeRequestedBook(player, active);

            active.requiredBookRead = true;
            active.progress = Math.max(active.progress, Math.max(1, def.goal()));
            active.readyToClaim = true;
            active.stage = ActiveQuestState.STAGE_ACTIVE;

            QuestManager.markDirty(player);
            QuestManager.refreshBossBar(player);
            QuestManager.syncQuest(player);

            player.sendMessage(Text.literal("Correct. Return to the librarian to claim your reward."), true);
        } else {
            QuestManager.failActiveQuest(player, "That answer was wrong. Return to the librarian to restart quest.");
        }
    }
}