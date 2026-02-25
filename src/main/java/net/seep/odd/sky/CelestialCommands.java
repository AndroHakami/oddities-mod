package net.seep.odd.sky;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.Identifier;

public final class CelestialCommands {
    private CelestialCommands() {}

    /** Register all /oddcelestial subcommands on the dispatcher. */
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("oddcelestial")
                        .requires(src -> src.hasPermissionLevel(2)) // OPs only; remove if you want everyone
                        .then(CommandManager.literal("invasion")
                                .then(CommandManager.literal("start")
                                        .executes(ctx -> {
                                            int waves = 6; // default
                                            net.seep.odd.event.alien.AlienInvasionInit.manager()
                                                    .start(ctx.getSource().getServer(), waves);

                                            ctx.getSource().sendFeedback(
                                                    () -> net.minecraft.text.Text.literal("Alien invasion started (" + waves + " waves)."), true);
                                            return 1;
                                        })
                                        .then(CommandManager.argument("waves", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 50))
                                                .executes(ctx -> {
                                                    int waves = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "waves");
                                                    net.seep.odd.event.alien.AlienInvasionInit.manager()
                                                            .start(ctx.getSource().getServer(), waves);

                                                    ctx.getSource().sendFeedback(
                                                            () -> net.minecraft.text.Text.literal("Alien invasion started (" + waves + " waves)."), true);
                                                    return 1;
                                                })
                                        )
                                )
                                .then(CommandManager.literal("stop")
                                        .executes(ctx -> {
                                            net.seep.odd.event.alien.AlienInvasionInit.manager().stop(ctx.getSource().getServer());
                                            ctx.getSource().sendFeedback(
                                                    () -> net.minecraft.text.Text.literal("Alien invasion stopped."), true);
                                            return 1;
                                        })
                                )
                        )
        );
    }
}
