package net.seep.odd.expeditions.atheneum;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import net.seep.odd.expeditions.Expeditions;

public final class AtheneumCommands {
    private AtheneumCommands() {}

    public static void register(CommandDispatcher<ServerCommandSource> d) {
        d.register(CommandManager.literal("atheneum")
                .requires(src -> src.hasPermissionLevel(2))
                .then(CommandManager.literal("tp")
                        .executes(ctx -> tpToDimension(ctx.getSource(), null))
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(ctx -> tpToDimension(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player")))
                        )
                )
        );
    }

    public static boolean teleportPlayerToAtheneum(ServerPlayerEntity p) {
        if (p == null) return false;

        MinecraftServer server = p.getServer();
        if (server == null) {
            p.sendMessage(Text.literal("Server unavailable."), true);
            return false;
        }

        ServerWorld dst = server.getWorld(Expeditions.ATHENEUM_WORLD);
        if (dst == null) {
            p.sendMessage(Text.literal("Atheneum world is not loaded / missing JSON."), true);
            return false;
        }

        p.stopRiding();
        p.setVelocity(Vec3d.ZERO);
        p.fallDistance = 0f;
        p.teleport(dst, 0.5, 5.0, 0.5, p.getYaw(), p.getPitch());
        return true;
    }

    private static int tpToDimension(ServerCommandSource src, ServerPlayerEntity target) {
        ServerPlayerEntity p = target != null ? target : src.getPlayer();
        if (p == null) {
            src.sendError(Text.literal("Player-only command."));
            return 0;
        }

        if (!teleportPlayerToAtheneum(p)) {
            src.sendError(Text.literal("Failed to teleport to Atheneum."));
            return 0;
        }

        src.sendFeedback(() -> Text.literal("Teleported " + p.getName().getString() + " to Atheneum."), true);
        return 1;
    }
}
