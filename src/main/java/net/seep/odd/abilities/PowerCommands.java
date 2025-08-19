package net.seep.odd.abilities;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.seep.odd.abilities.power.Powers;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class PowerCommands {
    private PowerCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) -> {
            dispatcher.register(literal("power")
                    .requires(src -> src.hasPermissionLevel(2))
                    .then(literal("set")
                            .then(argument("target", EntityArgumentType.player())
                                    .then(argument("id", StringArgumentType.string())
                                            .executes(ctx -> {
                                                ServerCommandSource src = ctx.getSource();
                                                ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "target");
                                                String id = StringArgumentType.getString(ctx, "id");
                                                if (!Powers.exists(id)) {
                                                    src.sendFeedback(() -> Text.literal("Unknown power id: " + id), false);
                                                    return 0;
                                                }
                                                PowerAPI.set(target, id, true);
                                                src.sendFeedback(() -> Text.literal("Set " + target.getName().getString() + " power to " + id), true);
                                                return 1;
                                            })
                                    )
                            )
                    )
                    .then(literal("get")
                            .executes(ctx -> {
                                ServerPlayerEntity self = ctx.getSource().getPlayer();
                                String id = PowerAPI.get(self);
                                ctx.getSource().sendFeedback(() -> Text.literal("Your power: " + (id.isEmpty() ? "<none>" : id)), false);
                                return 1;
                            })
                            .then(argument("target", EntityArgumentType.player())
                                    .executes(ctx -> {
                                        ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "target");
                                        String id = PowerAPI.get(target);
                                        ctx.getSource().sendFeedback(() -> Text.literal(target.getName().getString() + " power: " + (id.isEmpty() ? "<none>" : id)), false);
                                        return 1;
                                    })
                            )
                    )
                    .then(literal("clear")
                            .then(argument("target", EntityArgumentType.player())
                                    .executes(ctx -> {
                                        ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "target");
                                        PowerAPI.clear(target);
                                        ctx.getSource().sendFeedback(() -> Text.literal("Cleared power for " + target.getName().getString()), true);
                                        return 1;
                                    })
                            )
                    )
                    .then(literal("list").executes(ctx -> {
                        String list = String.join(", ", Powers.all().keySet());
                        ctx.getSource().sendFeedback(() -> Text.literal("Registered powers: " + (list.isEmpty() ? "<none>" : list)), false);
                        return 1;
                    }))
            );
        });
    }
}