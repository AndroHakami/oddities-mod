// FILE: src/main/java/net/seep/odd/block/gate/GateCommands.java
package net.seep.odd.block.gate;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.seep.odd.Oddities;

import static net.minecraft.server.command.CommandManager.*;

public final class GateCommands {
    private GateCommands(){}

    /** Call from your mod init: CommandRegistrationCallback.EVENT.register(GateCommands::register); */
    public static void register(CommandDispatcher<ServerCommandSource> d, CommandRegistryAccess access) {
        d.register(literal("odd")
                .then(literal("gate")

                        .then(literal("list").executes(ctx -> {
                            var sb = new StringBuilder("Gate styles: ");
                            for (GateStyle s : GateStyles.all()) sb.append(s.id()).append(" ");
                            ctx.getSource().sendFeedback(() -> Text.literal(sb.toString().trim()), false);
                            return 1;
                        }))

                        .then(literal("info")
                                .executes(ctx -> {
                                    DimensionalGateBlockEntity be = gateFromLook(ctx.getSource());
                                    if (be == null) return 0;
                                    ctx.getSource().sendFeedback(() -> Text.literal(
                                            "Gate @ " + be.getPos() +
                                                    " style=" + be.getStyleId() +
                                                    " dest=" + be.getDestWorldId()
                                    ), false);
                                    return 1;
                                })
                                .then(argument("pos", BlockPosArgumentType.blockPos())
                                        .executes(ctx -> {
                                            DimensionalGateBlockEntity be = gateFromPos(ctx.getSource(), BlockPosArgumentType.getBlockPos(ctx, "pos"));
                                            if (be == null) return 0;
                                            ctx.getSource().sendFeedback(() -> Text.literal(
                                                    "Gate @ " + be.getPos() +
                                                            " style=" + be.getStyleId() +
                                                            " dest=" + be.getDestWorldId()
                                            ), false);
                                            return 1;
                                        }))
                        )

                        .then(literal("style")
                                .then(argument("styleId", StringArgumentType.string())
                                        .executes(ctx -> {
                                            DimensionalGateBlockEntity be = gateFromLook(ctx.getSource());
                                            if (be == null) return 0;

                                            Identifier sid = parseIdDefaultOdd(ctx.getSource(),
                                                    StringArgumentType.getString(ctx, "styleId"),
                                                    "Invalid styleId");
                                            if (sid == null) return 0;

                                            // optional: validate it exists
                                            if (!GateStyles.exists(sid)) {
                                                ctx.getSource().sendError(Text.literal("Unknown style: " + sid + " (use /odd gate list)"));
                                                return 0;
                                            }

                                            // keep your signature style: setStyleId(id, syncToClient)
                                            be.setStyleId(sid, true);
                                            ctx.getSource().sendFeedback(() -> Text.literal("Gate style set to " + sid), true);
                                            return 1;
                                        })
                                        .then(argument("pos", BlockPosArgumentType.blockPos())
                                                .executes(ctx -> {
                                                    DimensionalGateBlockEntity be = gateFromPos(ctx.getSource(), BlockPosArgumentType.getBlockPos(ctx, "pos"));
                                                    if (be == null) return 0;

                                                    Identifier sid = parseIdDefaultOdd(ctx.getSource(),
                                                            StringArgumentType.getString(ctx, "styleId"),
                                                            "Invalid styleId");
                                                    if (sid == null) return 0;

                                                    if (!GateStyles.exists(sid)) {
                                                        ctx.getSource().sendError(Text.literal("Unknown style: " + sid + " (use /odd gate list)"));
                                                        return 0;
                                                    }

                                                    be.setStyleId(sid, true);
                                                    ctx.getSource().sendFeedback(() -> Text.literal("Gate style set to " + sid + " @ " + be.getPos()), true);
                                                    return 1;
                                                }))
                                )
                        )

                        .then(literal("dest")
                                .then(argument("worldId", StringArgumentType.string())
                                        .executes(ctx -> {
                                            DimensionalGateBlockEntity be = gateFromLook(ctx.getSource());
                                            if (be == null) return 0;

                                            Identifier wid = parseIdDefaultOdd(ctx.getSource(),
                                                    StringArgumentType.getString(ctx, "worldId"),
                                                    "Invalid worldId");
                                            if (wid == null) return 0;

                                            be.setDestWorldId(wid);
                                            ctx.getSource().sendFeedback(() -> Text.literal("Gate destination set to " + wid), true);
                                            return 1;
                                        })
                                        .then(argument("pos", BlockPosArgumentType.blockPos())
                                                .executes(ctx -> {
                                                    DimensionalGateBlockEntity be = gateFromPos(ctx.getSource(), BlockPosArgumentType.getBlockPos(ctx, "pos"));
                                                    if (be == null) return 0;

                                                    Identifier wid = parseIdDefaultOdd(ctx.getSource(),
                                                            StringArgumentType.getString(ctx, "worldId"),
                                                            "Invalid worldId");
                                                    if (wid == null) return 0;

                                                    be.setDestWorldId(wid);
                                                    ctx.getSource().sendFeedback(() -> Text.literal("Gate destination set to " + wid + " @ " + be.getPos()), true);
                                                    return 1;
                                                }))
                                )
                        )

                        // apply = style + default destination (from GateStyles)
                        .then(literal("apply")
                                .then(argument("styleId", StringArgumentType.string())
                                        .executes(ctx -> {
                                            DimensionalGateBlockEntity be = gateFromLook(ctx.getSource());
                                            if (be == null) return 0;

                                            Identifier sid = parseIdDefaultOdd(ctx.getSource(),
                                                    StringArgumentType.getString(ctx, "styleId"),
                                                    "Invalid styleId");
                                            if (sid == null) return 0;

                                            GateStyle style = GateStyles.get(sid);
                                            if (style == null) {
                                                ctx.getSource().sendError(Text.literal("Unknown style: " + sid + " (use /odd gate list)"));
                                                return 0;
                                            }

                                            be.setStyleId(style.id(), true);
                                            be.setDestWorldId(style.defaultDestWorldId());

                                            ctx.getSource().sendFeedback(() -> Text.literal("Gate applied style=" + style.id() + " dest=" + style.defaultDestWorldId()), true);
                                            return 1;
                                        })
                                        .then(argument("pos", BlockPosArgumentType.blockPos())
                                                .executes(ctx -> {
                                                    DimensionalGateBlockEntity be = gateFromPos(ctx.getSource(), BlockPosArgumentType.getBlockPos(ctx, "pos"));
                                                    if (be == null) return 0;

                                                    Identifier sid = parseIdDefaultOdd(ctx.getSource(),
                                                            StringArgumentType.getString(ctx, "styleId"),
                                                            "Invalid styleId");
                                                    if (sid == null) return 0;

                                                    GateStyle style = GateStyles.get(sid);
                                                    if (style == null) {
                                                        ctx.getSource().sendError(Text.literal("Unknown style: " + sid + " (use /odd gate list)"));
                                                        return 0;
                                                    }

                                                    be.setStyleId(style.id(), true);
                                                    be.setDestWorldId(style.defaultDestWorldId());

                                                    ctx.getSource().sendFeedback(() -> Text.literal("Gate applied style=" + style.id() + " dest=" + style.defaultDestWorldId() + " @ " + be.getPos()), true);
                                                    return 1;
                                                }))
                                )
                        )
                )
        );
    }

    /**
     * Parses an Identifier from user input, but if no namespace is given,
     * defaults to your mod id (odd:<input>).
     *
     * Examples:
     *  - "rotten_roots" -> odd:rotten_roots
     *  - "odd:atheneum" -> odd:atheneum
     *  - "minecraft:the_nether" -> minecraft:the_nether
     */
    private static Identifier parseIdDefaultOdd(ServerCommandSource src, String raw, String err) {
        raw = raw.trim();
        Identifier id;
        if (raw.contains(":")) {
            id = Identifier.tryParse(raw);
        } else {
            id = Identifier.tryParse(Oddities.MOD_ID + ":" + raw);
        }

        if (id == null) {
            src.sendError(Text.literal(err + ": " + raw + " (expected namespace:path OR path)"));
            return null;
        }
        return id;
    }

    private static DimensionalGateBlockEntity gateFromLook(ServerCommandSource src) {
        ServerPlayerEntity p = src.getPlayer();
        if (p == null) { src.sendError(Text.literal("Player-only.")); return null; }

        HitResult hr = p.raycast(96.0, 1.0f, false);
        if (hr.getType() != HitResult.Type.BLOCK) { src.sendError(Text.literal("Look at a gate.")); return null; }

        BlockPos hit = ((BlockHitResult) hr).getBlockPos();
        return gateFromHitBlock(src, p, hit);
    }

    private static DimensionalGateBlockEntity gateFromPos(ServerCommandSource src, BlockPos pos) {
        ServerPlayerEntity p = src.getPlayer();
        if (p == null) { src.sendError(Text.literal("Player-only.")); return null; }
        return gateFromHitBlock(src, p, pos);
    }

    private static DimensionalGateBlockEntity gateFromHitBlock(ServerCommandSource src, ServerPlayerEntity p, BlockPos hit) {
        var st = p.getWorld().getBlockState(hit);
        if (!(st.getBlock() instanceof DimensionalGateBlock)) { src.sendError(Text.literal("Not a gate.")); return null; }

        BlockPos base = DimensionalGateBlock.getBasePos(hit, st);
        var be = p.getWorld().getBlockEntity(base);
        if (!(be instanceof DimensionalGateBlockEntity gateBe)) {
            src.sendError(Text.literal("Gate controller missing BE at " + base));
            return null;
        }
        return gateBe;
    }
}
