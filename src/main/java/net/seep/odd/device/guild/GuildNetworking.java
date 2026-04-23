package net.seep.odd.device.guild;

import java.util.UUID;

import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;

public final class GuildNetworking {
    private GuildNetworking() {}

    public static final Identifier C2S_REQUEST_SYNC      = new Identifier(Oddities.MOD_ID, "guild_request_sync");
    public static final Identifier S2C_SYNC              = new Identifier(Oddities.MOD_ID, "guild_sync");
    public static final Identifier C2S_CREATE_TEAM       = new Identifier(Oddities.MOD_ID, "guild_create_team");
    public static final Identifier C2S_LEAVE_TEAM        = new Identifier(Oddities.MOD_ID, "guild_leave_team");
    public static final Identifier C2S_SET_STATUS        = new Identifier(Oddities.MOD_ID, "guild_set_status");
    public static final Identifier C2S_SET_NOTES         = new Identifier(Oddities.MOD_ID, "guild_set_notes");
    public static final Identifier C2S_INVITE            = new Identifier(Oddities.MOD_ID, "guild_invite");
    public static final Identifier C2S_UPDATE_TEAM_META  = new Identifier(Oddities.MOD_ID, "guild_update_team_meta");
    public static final Identifier C2S_KICK_MEMBER       = new Identifier(Oddities.MOD_ID, "guild_kick_member");
    public static final Identifier C2S_DISBAND_TEAM      = new Identifier(Oddities.MOD_ID, "guild_disband_team");

    private static boolean inited = false;

    public static void init() {
        if (inited) return;
        inited = true;

        ServerLifecycleEvents.SERVER_STARTED.register(GuildManager::load);
        ServerLifecycleEvents.SERVER_STOPPING.register(GuildManager::save);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                server.execute(() -> {
                    GuildManager.syncScoreboard(server);
                    broadcastSync(server);
                }));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                server.execute(() -> {
                    GuildManager.syncScoreboard(server);
                    broadcastSync(server);
                }));

        registerCommands();

        ServerPlayNetworking.registerGlobalReceiver(C2S_REQUEST_SYNC, (server, player, handler, buf, sender) ->
                server.execute(() -> sendSync(player)));

        ServerPlayNetworking.registerGlobalReceiver(C2S_CREATE_TEAM, (server, player, handler, buf, sender) -> {
            String name = buf.readString(GuildManager.NAME_MAX_LEN);
            String prefix = buf.readString(GuildManager.PREFIX_MAX_LEN);
            String colorId = buf.readString(24);

            server.execute(() -> {
                String error = GuildManager.createTeam(player, name, prefix, colorId);
                if (error != null) {
                    player.sendMessage(Text.literal(error), true);
                    sendSync(player);
                    return;
                }
                broadcastSync(server);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(C2S_LEAVE_TEAM, (server, player, handler, buf, sender) ->
                server.execute(() -> {
                    String error = GuildManager.leaveTeam(player);
                    if (error != null) {
                        player.sendMessage(Text.literal(error), true);
                        sendSync(player);
                        return;
                    }
                    player.sendMessage(Text.literal("Team updated."), true);
                    broadcastSync(server);
                }));

        ServerPlayNetworking.registerGlobalReceiver(C2S_SET_STATUS, (server, player, handler, buf, sender) -> {
            String status = buf.readString(GuildManager.STATUS_MAX_LEN);
            server.execute(() -> {
                String error = GuildManager.setMemberStatus(player, status);
                if (error != null) {
                    player.sendMessage(Text.literal(error), true);
                    sendSync(player);
                    return;
                }
                broadcastSync(server);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(C2S_SET_NOTES, (server, player, handler, buf, sender) -> {
            String notes = buf.readString(GuildManager.NOTES_MAX_LEN);
            server.execute(() -> {
                String error = GuildManager.setTeamNotes(player, notes);
                if (error != null) {
                    player.sendMessage(Text.literal(error), true);
                    sendSync(player);
                    return;
                }
                broadcastSync(server);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(C2S_INVITE, (server, player, handler, buf, sender) -> {
            String target = buf.readString(64);
            server.execute(() -> {
                String error = GuildManager.invitePlayer(player, target);
                if (error != null) {
                    player.sendMessage(Text.literal(error), true);
                    sendSync(player);
                    return;
                }
                sendSync(player);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(C2S_UPDATE_TEAM_META, (server, player, handler, buf, sender) -> {
            String name = buf.readString(GuildManager.NAME_MAX_LEN);
            String prefix = buf.readString(GuildManager.PREFIX_MAX_LEN);
            String colorId = buf.readString(24);
            server.execute(() -> {
                String error = GuildManager.updateTeamMeta(player, name, prefix, colorId);
                if (error != null) {
                    player.sendMessage(Text.literal(error), true);
                    sendSync(player);
                    return;
                }
                broadcastSync(server);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(C2S_KICK_MEMBER, (server, player, handler, buf, sender) -> {
            UUID memberUuid = buf.readUuid();
            server.execute(() -> {
                String error = GuildManager.kickMember(player, memberUuid);
                if (error != null) {
                    player.sendMessage(Text.literal(error), true);
                    sendSync(player);
                    return;
                }
                broadcastSync(server);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(C2S_DISBAND_TEAM, (server, player, handler, buf, sender) ->
                server.execute(() -> {
                    String error = GuildManager.disbandTeam(player);
                    if (error != null) {
                        player.sendMessage(Text.literal(error), true);
                        sendSync(player);
                        return;
                    }
                    broadcastSync(server);
                }));
    }

    private static void registerCommands() {
        CommandRegistrationCallback.EVENT.register(GuildNetworking::registerCommandTree);
    }

    private static void registerCommandTree(com.mojang.brigadier.CommandDispatcher<ServerCommandSource> dispatcher,
                                            CommandRegistryAccess registryAccess,
                                            CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(
                CommandManager.literal("oddguildaccept")
                        .then(CommandManager.argument("inviteId", StringArgumentType.word())
                                .executes(ctx -> {
                                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                                    if (player == null) return 0;

                                    String inviteId = StringArgumentType.getString(ctx, "inviteId");
                                    String error = GuildManager.acceptInvite(player, inviteId);
                                    if (error != null) {
                                        player.sendMessage(Text.literal(error), true);
                                        sendSync(player);
                                        return 0;
                                    }

                                    MinecraftServer server = player.getServer();
                                    if (server != null) {
                                        broadcastSync(server);
                                    }
                                    player.sendMessage(Text.literal("You joined the team."), true);
                                    return 1;
                                }))
        );
    }

    public static void sendSync(ServerPlayerEntity player) {
        PacketByteBuf buf = PacketByteBufs.create();

        GuildTeam team = GuildManager.getPreparedTeamFor(player.getUuid(), player.getServer());
        buf.writeBoolean(team != null);
        if (team != null) {
            team.write(buf);
        }

        ServerPlayNetworking.send(player, S2C_SYNC, buf);
    }

    public static void broadcastSync(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            sendSync(player);
        }
    }
}
