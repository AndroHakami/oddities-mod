package net.seep.odd.worldgen.structure;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public final class WitchlogDebugCommand {
    private WitchlogDebugCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                CommandManager.literal("placewitchlog")
                        .requires(source -> source.hasPermissionLevel(2))

                        .then(CommandManager.literal("first_pack")
                                .executes(WitchlogDebugCommand::placeFirstPackAtPlayerOffset)
                                .then(CommandManager.argument("x", IntegerArgumentType.integer())
                                        .then(CommandManager.argument("y", IntegerArgumentType.integer())
                                                .then(CommandManager.argument("z", IntegerArgumentType.integer())
                                                        .executes(ctx -> placeFirstPackAtCoords(
                                                                ctx,
                                                                IntegerArgumentType.getInteger(ctx, "x"),
                                                                IntegerArgumentType.getInteger(ctx, "y"),
                                                                IntegerArgumentType.getInteger(ctx, "z")
                                                        ))
                                                )
                                        )
                                )
                        )

                        .then(CommandManager.literal("arena")
                                .executes(WitchlogDebugCommand::placeArenaAtPlayerOffset)
                                .then(CommandManager.argument("x", IntegerArgumentType.integer())
                                        .then(CommandManager.argument("y", IntegerArgumentType.integer())
                                                .then(CommandManager.argument("z", IntegerArgumentType.integer())
                                                        .executes(ctx -> placeArenaAtCoords(
                                                                ctx,
                                                                IntegerArgumentType.getInteger(ctx, "x"),
                                                                IntegerArgumentType.getInteger(ctx, "y"),
                                                                IntegerArgumentType.getInteger(ctx, "z")
                                                        ))
                                                )
                                        )
                                )
                        )

                        .then(CommandManager.literal("transition")
                                .executes(WitchlogDebugCommand::placeTransitionAtPlayerOffset)
                                .then(CommandManager.argument("x", IntegerArgumentType.integer())
                                        .then(CommandManager.argument("y", IntegerArgumentType.integer())
                                                .then(CommandManager.argument("z", IntegerArgumentType.integer())
                                                        .executes(ctx -> placeTransitionAtCoords(
                                                                ctx,
                                                                IntegerArgumentType.getInteger(ctx, "x"),
                                                                IntegerArgumentType.getInteger(ctx, "y"),
                                                                IntegerArgumentType.getInteger(ctx, "z")
                                                        ))
                                                )
                                        )
                                )
                        )

                        .then(CommandManager.literal("arena_stack")
                                .executes(WitchlogDebugCommand::placeArenaStackAtPlayerOffset)
                                .then(CommandManager.argument("x", IntegerArgumentType.integer())
                                        .then(CommandManager.argument("y", IntegerArgumentType.integer())
                                                .then(CommandManager.argument("z", IntegerArgumentType.integer())
                                                        .executes(ctx -> placeArenaStackAtCoords(
                                                                ctx,
                                                                IntegerArgumentType.getInteger(ctx, "x"),
                                                                IntegerArgumentType.getInteger(ctx, "y"),
                                                                IntegerArgumentType.getInteger(ctx, "z")
                                                        ))
                                                )
                                        )
                                )
                        )

                        // backwards-compatible default
                        .executes(WitchlogDebugCommand::placeFirstPackAtPlayerOffset)
        ));
    }

    private static BlockPos playerOffset(CommandContext<ServerCommandSource> ctx) {
        return BlockPos.ofFloored(ctx.getSource().getPosition()).add(0, 0, 20);
    }

    private static int placeFirstPackAtPlayerOffset(CommandContext<ServerCommandSource> ctx) {
        return feedbackFirstPack(ctx, playerOffset(ctx));
    }

    private static int placeFirstPackAtCoords(CommandContext<ServerCommandSource> ctx, int x, int y, int z) {
        return feedbackFirstPack(ctx, new BlockPos(x, y, z));
    }

    private static int placeArenaAtPlayerOffset(CommandContext<ServerCommandSource> ctx) {
        return feedbackArena(ctx, playerOffset(ctx));
    }

    private static int placeArenaAtCoords(CommandContext<ServerCommandSource> ctx, int x, int y, int z) {
        return feedbackArena(ctx, new BlockPos(x, y, z));
    }

    private static int placeTransitionAtPlayerOffset(CommandContext<ServerCommandSource> ctx) {
        return feedbackTransition(ctx, playerOffset(ctx));
    }

    private static int placeTransitionAtCoords(CommandContext<ServerCommandSource> ctx, int x, int y, int z) {
        return feedbackTransition(ctx, new BlockPos(x, y, z));
    }

    private static int placeArenaStackAtPlayerOffset(CommandContext<ServerCommandSource> ctx) {
        return feedbackArenaStack(ctx, playerOffset(ctx));
    }

    private static int placeArenaStackAtCoords(CommandContext<ServerCommandSource> ctx, int x, int y, int z) {
        return feedbackArenaStack(ctx, new BlockPos(x, y, z));
    }

    private static int feedbackFirstPack(CommandContext<ServerCommandSource> ctx, BlockPos origin) {
        ServerCommandSource source = ctx.getSource();
        ServerWorld world = source.getWorld();

        int placed = WitchlogStructureAssembler.placeFirstPack(world, origin);
        source.sendFeedback(() -> Text.literal(
                "[Witchlog] Placed first pack (" + placed + " templates) at tower center origin " +
                        origin.getX() + ", " + origin.getY() + ", " + origin.getZ()
        ), true);
        return placed > 0 ? 1 : 0;
    }

    private static int feedbackArena(CommandContext<ServerCommandSource> ctx, BlockPos origin) {
        ServerCommandSource source = ctx.getSource();
        ServerWorld world = source.getWorld();

        int placed = WitchlogStructureAssembler.placeArenaPack(world, origin);
        source.sendFeedback(() -> Text.literal(
                "[Witchlog] Placed arena v2 (" + placed + " templates) at arena center top " +
                        origin.getX() + ", " + origin.getY() + ", " + origin.getZ()
        ), true);
        return placed > 0 ? 1 : 0;
    }

    private static int feedbackTransition(CommandContext<ServerCommandSource> ctx, BlockPos origin) {
        ServerCommandSource source = ctx.getSource();
        ServerWorld world = source.getWorld();

        int placed = WitchlogStructureAssembler.placeTransitionPack(world, origin);
        source.sendFeedback(() -> Text.literal(
                "[Witchlog] Placed transition pack (" + placed + " templates) using arena center top " +
                        origin.getX() + ", " + origin.getY() + ", " + origin.getZ()
        ), true);
        return placed > 0 ? 1 : 0;
    }

    private static int feedbackArenaStack(CommandContext<ServerCommandSource> ctx, BlockPos origin) {
        ServerCommandSource source = ctx.getSource();
        ServerWorld world = source.getWorld();

        int placed = WitchlogStructureAssembler.placeArenaStack(world, origin);
        source.sendFeedback(() -> Text.literal(
                "[Witchlog] Placed arena stack (" + placed + " templates) at arena center top " +
                        origin.getX() + ", " + origin.getY() + ", " + origin.getZ()
        ), true);
        return placed > 0 ? 1 : 0;
    }
}
