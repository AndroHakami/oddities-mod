package net.seep.odd.quest;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import net.seep.odd.lore.AtheneumLoreBooks;

public final class BookReadTracker {
    private BookReadTracker() {}

    public static void init() {
        UseItemCallback.EVENT.register(BookReadTracker::onUse);
    }

    private static TypedActionResult<ItemStack> onUse(PlayerEntity player, World world, Hand hand) {
        ItemStack used = player.getStackInHand(hand);

        if (world.isClient) {
            return TypedActionResult.pass(used);
        }

        if (!(player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer)) {
            return TypedActionResult.pass(used);
        }

        var active = QuestManager.profile(serverPlayer).activeQuest;
        if (active == null) {
            return TypedActionResult.pass(used);
        }

        var def = QuestRegistry.get(active.questId);
        if (def == null || def.objective.type != QuestObjectiveData.Type.LORE_BOOK_QUIZ) {
            return TypedActionResult.pass(used);
        }

        if (AtheneumLoreBooks.matchesRequested(used, active.requestedLoreId)) {
            active.progress = 1; // or whatever flag/field you use for "read"
            QuestManager.markDirty(serverPlayer);
            QuestManager.syncQuest(serverPlayer);
        }

        // IMPORTANT: pass, so vanilla still opens/uses the book normally
        return TypedActionResult.pass(used);
    }
}