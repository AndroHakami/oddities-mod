package net.seep.odd.expeditions.atheneum;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import net.seep.odd.expeditions.Expeditions;

public final class AtheneumCommands {
    private AtheneumCommands() {}

    public static void register(CommandDispatcher<ServerCommandSource> d) {
        d.register(CommandManager.literal("atheneum")
                .requires(src -> src.hasPermissionLevel(2))
                .then(CommandManager.literal("tp")
                        .executes(ctx -> tpToDimension(ctx.getSource()))
                )
        );
    }

    private static int tpToDimension(ServerCommandSource src) {
        ServerPlayerEntity p = src.getPlayer();
        if (p == null) { src.sendError(Text.literal("Player-only command.")); return 0; }

        MinecraftServer server = p.getServer();
        ServerWorld dst = server.getWorld(Expeditions.ATHENEUM_WORLD);
        if (dst == null) {
            src.sendError(Text.literal("Atheneum world is not loaded / missing JSON."));
            return 0;
        }

        // Spawn plaza is forced-open near (0,0)
        p.teleport(dst, 0.5, 5.0, 0.5, p.getYaw(), p.getPitch());
        src.sendFeedback(() -> Text.literal("Teleported to Atheneum."), true);
        return 1;
    }
}
