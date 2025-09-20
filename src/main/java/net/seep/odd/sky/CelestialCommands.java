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
                        .then(CommandManager.literal("alien").executes(ctx -> {
                            var sw = ctx.getSource().getWorld();

                            // lime/alien vibe
                            float hue = 260f, sat = 1.35f, val = 1.00f, nightLift = 0.3f;
                            boolean hideClouds = true;
                            int duration = 20 * 60;          // 60 seconds
                            float sunScale = 3.8f, moonScale = 7.2f;

                            Identifier sun  = new Identifier("odd", "textures/environment/alien_sun.png");
                            Identifier moon = new Identifier("odd", "textures/environment/alien_moon.png");

                            CelestialEventS2C.sendAlien(sw, sun, moon, hue, sat, val, nightLift,
                                    sunScale, moonScale, hideClouds, duration);

                            ctx.getSource().sendFeedback(
                                    () -> net.minecraft.text.Text.literal("Alien sky activated."), true);
                            return 1;
                        }))
                        .then(CommandManager.literal("clear").executes(ctx -> {
                            CelestialEventS2C.sendClear(ctx.getSource().getWorld());
                            ctx.getSource().sendFeedback(
                                    () -> net.minecraft.text.Text.literal("Celestial events cleared."), true);
                            return 1;
                        }))
        );
    }
}
