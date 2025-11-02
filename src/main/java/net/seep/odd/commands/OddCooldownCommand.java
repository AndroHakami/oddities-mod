// src/main/java/net/seep/odd/commands/OddCooldownCommand.java
package net.seep.odd.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.seep.odd.abilities.data.PowerCooldownOverrides;

public final class OddCooldownCommand {
    private OddCooldownCommand(){}
    public static void register(CommandDispatcher<ServerCommandSource> d) {
        d.register(CommandManager.literal("odd")
                .then(CommandManager.literal("cd")
                        .then(CommandManager.argument("power", com.mojang.brigadier.arguments.StringArgumentType.string())
                                .then(CommandManager.argument("slot",  com.mojang.brigadier.arguments.StringArgumentType.string())
                                        .then(CommandManager.argument("ticks", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0))
                                                .executes(ctx -> {
                                                    String id   = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "power");
                                                    String slot = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "slot");
                                                    int ticks   = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "ticks");
                                                    PowerCooldownOverrides.get(ctx.getSource().getServer())
                                                            .set(id + "#" + slot, ticks);
                                                    ctx.getSource().sendFeedback(() -> net.minecraft.text.Text.literal(
                                                            "Set cooldown override for " + id + "#" + slot + " = " + ticks + "t"), true);
                                                    return 1;
                                                })))))
                .then(CommandManager.literal("cdclear")
                        .then(CommandManager.argument("power", com.mojang.brigadier.arguments.StringArgumentType.string())
                                .then(CommandManager.argument("slot",  com.mojang.brigadier.arguments.StringArgumentType.string())
                                        .executes(ctx -> {
                                            String id   = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "power");
                                            String slot = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "slot");
                                            PowerCooldownOverrides.get(ctx.getSource().getServer()).clear(id + "#" + slot);
                                            ctx.getSource().sendFeedback(() -> net.minecraft.text.Text.literal(
                                                    "Cleared cooldown override for " + id + "#" + slot), true);
                                            return 1;
                                        }))))
        );
    }
}
