package net.seep.odd.worldgen.structure;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public final class WitchlogDebugCommand {
    private WitchlogDebugCommand() {}

    @FunctionalInterface
    private interface PlacementAction {
        int place(ServerWorld world, BlockPos origin);
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                CommandManager.literal("placewitchlog")
                        .requires(source -> source.hasPermissionLevel(2))

                        .then(subcommand(
                                "first_pack",
                                "tower base center",
                                40,
                                WitchlogStructureAssembler::placeFirstPack
                        ))

                        .then(subcommand(
                                "arena",
                                "arena platform center / top surface",
                                60,
                                WitchlogStructureAssembler::placeArena
                        ))

                        .then(subcommand(
                                "transition",
                                "arena platform center / top surface",
                                60,
                                WitchlogStructureAssembler::placeArenaTransition
                        ))

                        .then(subcommand(
                                "arena_stack",
                                "arena platform center / top surface",
                                80,
                                WitchlogStructureAssembler::placeArenaStack
                        ))

                        .then(subcommand(
                                "full",
                                "arena platform center / top surface",
                                100,
                                WitchlogStructureAssembler::placeFullStructure
                        ))

                        .then(subcommand(
                                "room_apothecary",
                                "doorway threshold center",
                                16,
                                WitchlogStructureAssembler::placeApothecaryMedium
                        ))
        ));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> subcommand(
            String name,
            String originMeaning,
            int defaultForwardOffset,
            PlacementAction action
    ) {
        return CommandManager.literal(name)

                // /placewitchlog <name>
                .executes(ctx -> executeAtPlayerOffset(ctx, name, originMeaning, defaultForwardOffset, action))

                // /placewitchlog <name> <x> <y> <z>
                .then(CommandManager.argument("x", IntegerArgumentType.integer())
                        .then(CommandManager.argument("y", IntegerArgumentType.integer())
                                .then(CommandManager.argument("z", IntegerArgumentType.integer())
                                        .executes(ctx -> executeAtCoords(
                                                ctx,
                                                name,
                                                originMeaning,
                                                action,
                                                IntegerArgumentType.getInteger(ctx, "x"),
                                                IntegerArgumentType.getInteger(ctx, "y"),
                                                IntegerArgumentType.getInteger(ctx, "z")
                                        ))
                                )
                        )
                );
    }

    private static int executeAtPlayerOffset(
            CommandContext<ServerCommandSource> ctx,
            String name,
            String originMeaning,
            int defaultForwardOffset,
            PlacementAction action
    ) {
        ServerCommandSource source = ctx.getSource();
        ServerWorld world = source.getWorld();

        BlockPos origin = BlockPos.ofFloored(source.getPosition()).add(0, 0, defaultForwardOffset);
        int placed = action.place(world, origin);

        source.sendFeedback(() -> Text.literal(
                "[Witchlog] '" + name + "' placed " + placed + " template piece(s) at " +
                        origin.getX() + ", " + origin.getY() + ", " + origin.getZ() +
                        " | origin = " + originMeaning
        ), true);

        return placed > 0 ? placed : 0;
    }

    private static int executeAtCoords(
            CommandContext<ServerCommandSource> ctx,
            String name,
            String originMeaning,
            PlacementAction action,
            int x,
            int y,
            int z
    ) {
        ServerCommandSource source = ctx.getSource();
        ServerWorld world = source.getWorld();

        BlockPos origin = new BlockPos(x, y, z);
        int placed = action.place(world, origin);

        source.sendFeedback(() -> Text.literal(
                "[Witchlog] '" + name + "' placed " + placed + " template piece(s) at " +
                        origin.getX() + ", " + origin.getY() + ", " + origin.getZ() +
                        " | origin = " + originMeaning
        ), true);

        return placed > 0 ? placed : 0;
    }
}