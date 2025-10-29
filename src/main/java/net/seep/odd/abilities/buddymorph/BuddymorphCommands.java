package net.seep.odd.abilities.buddymorph;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;

public final class BuddymorphCommands {
    private BuddymorphCommands(){}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> {
            dispatcher.register(
                    CommandManager.literal("buddymorph")
                            .then(CommandManager.literal("debug").executes(BuddymorphCommands::debug))
                            .then(CommandManager.literal("add")
                                    .then(CommandManager.argument("id", net.minecraft.command.argument.IdentifierArgumentType.identifier())
                                            .executes(BuddymorphCommands::add)))
            );
        });
    }

    private static int debug(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerCommandSource src = ctx.getSource();
        PlayerEntity p = src.getPlayerOrThrow();
        var data = BuddymorphData.get(src.getServer().getOverworld());
        var list = new ArrayList<>(data.getList(p.getUuid()));
        src.sendFeedback(() -> Text.literal("[BM debug] size=" + list.size() + " -> " + list), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int add(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerCommandSource src = ctx.getSource();
        PlayerEntity p = src.getPlayerOrThrow();
        Identifier id = net.minecraft.command.argument.IdentifierArgumentType.getIdentifier(ctx, "id");
        var data = BuddymorphData.get(src.getServer().getOverworld());
        data.addBuddy(p.getUuid(), id);
        src.sendFeedback(() -> Text.literal("[BM debug] added " + id + " size=" + data.getList(p.getUuid()).size()), false);
        // push live update if screen is open
        if (p instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
            BuddymorphNet.s2cUpdateMenu(sp, new ArrayList<>(data.getList(p.getUuid())));
        }
        return Command.SINGLE_SUCCESS;
    }
}
