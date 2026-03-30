package net.seep.odd.lore;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class RottenRootsLoreCommand {
    private RottenRootsLoreCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(
                        CommandManager.literal("giverottenbook")
                                .requires(source -> source.hasPermissionLevel(2))
                                .executes(ctx -> giveRottenBook(ctx.getSource()))
                )
        );
    }

    private static int giveRottenBook(ServerCommandSource source) {
        try {
            ServerPlayerEntity player = source.getPlayerOrThrow();

            player.giveItemStack(RottenRootsLoreBooks.createRottenLogs1());

            source.sendFeedback(() -> Text.literal("Gave Rotten Logs [1/6]."), false);
            return 1;
        } catch (Exception e) {
            source.sendError(Text.literal("Could not give Rotten Roots lore book."));
            return 0;
        }
    }
}