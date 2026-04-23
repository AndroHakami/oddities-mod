package net.seep.odd.device.guild;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;

public final class GuildManager {
    private GuildManager() {}

    public static final int NAME_MAX_LEN = 32;
    public static final int PREFIX_MAX_LEN = 16;
    public static final int STATUS_MAX_LEN = 80;
    public static final int NOTES_MAX_LEN = 4000;
    private static final long INVITE_DURATION_MS = 1000L * 60L * 5L;
    private static final String SCOREBOARD_TEAM_PREFIX = "og";

    private static final List<GuildTeam> TEAMS = new ArrayList<>();
    private static final List<PendingInvite> INVITES = new ArrayList<>();

    public static void load(MinecraftServer server) {
        TEAMS.clear();
        INVITES.clear();

        Path file = saveFile(server);
        if (!Files.exists(file)) {
            syncScoreboard(server);
            return;
        }

        try (InputStream in = Files.newInputStream(file)) {
            NbtCompound root = NbtIo.readCompressed(in);
            if (root != null) {
                NbtList teams = root.getList("Teams", 10);
                for (int i = 0; i < teams.size(); i++) {
                    TEAMS.add(GuildTeam.fromNbt((NbtCompound) teams.get(i)));
                }
                TEAMS.sort(Comparator.comparingLong((GuildTeam t) -> t.createdAt));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        syncScoreboard(server);
    }

    public static void save(MinecraftServer server) {
        try {
            Path dir = saveDir(server);
            Files.createDirectories(dir);

            NbtCompound root = new NbtCompound();
            NbtList teams = new NbtList();
            for (GuildTeam team : TEAMS) {
                teams.add(team.toNbt());
            }
            root.put("Teams", teams);

            try (OutputStream out = Files.newOutputStream(saveFile(server))) {
                NbtIo.writeCompressed(root, out);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static GuildTeam getTeamFor(UUID playerUuid) {
        for (GuildTeam team : TEAMS) {
            if (team.isMember(playerUuid)) {
                return team;
            }
        }
        return null;
    }

    public static GuildTeam getPreparedTeamFor(UUID playerUuid, MinecraftServer server) {
        GuildTeam team = getTeamFor(playerUuid);
        if (team == null) return null;

        GuildTeam copy = new GuildTeam(team.id, team.ownerUuid, team.createdAt, team.name, team.prefix, team.colorId, team.notes);
        for (GuildMemberData member : team.members) {
            GuildMemberData clone = new GuildMemberData(member.uuid, displayName(server, member), member.status, member.joinedAt);
            clone.online = server.getPlayerManager().getPlayer(member.uuid) != null;
            copy.members.add(clone);
        }
        return copy;
    }

    public static String createTeam(ServerPlayerEntity player, String name, String prefix, String colorId) {
        name = normalizeInline(name);
        prefix = normalizeInline(prefix);

        if (getTeamFor(player.getUuid()) != null) return "You're already in a team.";
        if (name.isEmpty()) return "Team name can't be empty.";
        if (name.length() > NAME_MAX_LEN) return "Team name is too long.";

        String prefixError = validatePrefix(prefix);
        if (prefixError != null) return prefixError;

        for (GuildTeam existing : TEAMS) {
            if (existing.name.equalsIgnoreCase(name)) {
                return "That team name is already taken.";
            }
        }

        GuildColorOption color = GuildColorOption.byId(colorId);
        GuildTeam team = new GuildTeam(
                UUID.randomUUID(),
                player.getUuid(),
                System.currentTimeMillis(),
                name,
                prefix,
                color.id(),
                ""
        );
        team.members.add(new GuildMemberData(
                player.getUuid(),
                player.getName().getString(),
                "",
                System.currentTimeMillis()
        ));

        TEAMS.add(team);

        MinecraftServer server = player.getServer();
        if (server != null) {
            syncScoreboard(server);
            save(server);
        }
        return null;
    }

    public static String leaveTeam(ServerPlayerEntity player) {
        GuildTeam team = getTeamFor(player.getUuid());
        if (team == null) return "You're not in a team.";

        if (isLeader(team, player.getUuid())) {
            return disbandTeam(player);
        }

        boolean removed = team.members.removeIf(member -> member.uuid.equals(player.getUuid()));
        if (!removed) return "You're not in a team.";

        INVITES.removeIf(invite -> invite.inviteeUuid.equals(player.getUuid()));

        MinecraftServer server = player.getServer();
        if (server != null) {
            syncScoreboard(server);
            save(server);
        }
        return null;
    }

    public static String disbandTeam(ServerPlayerEntity player) {
        GuildTeam team = getTeamFor(player.getUuid());
        if (team == null) return "You're not in a team.";
        if (!isLeader(team, player.getUuid())) return "Only the team leader can do that.";

        INVITES.removeIf(invite -> invite.teamId.equals(team.id));
        TEAMS.remove(team);

        MinecraftServer server = player.getServer();
        if (server != null) {
            syncScoreboard(server);
            save(server);
        }
        return null;
    }

    public static String updateTeamMeta(ServerPlayerEntity player, String name, String prefix, String colorId) {
        GuildTeam team = getTeamFor(player.getUuid());
        if (team == null) return "You're not in a team.";
        if (!isLeader(team, player.getUuid())) return "Only the team leader can do that.";

        name = normalizeInline(name);
        prefix = normalizeInline(prefix);

        if (name.isEmpty()) return "Team name can't be empty.";
        if (name.length() > NAME_MAX_LEN) return "Team name is too long.";

        String prefixError = validatePrefix(prefix);
        if (prefixError != null) return prefixError;

        for (GuildTeam existing : TEAMS) {
            if (existing != team && existing.name.equalsIgnoreCase(name)) {
                return "That team name is already taken.";
            }
        }

        GuildColorOption color = GuildColorOption.byId(colorId);
        team.name = name;
        team.prefix = prefix;
        team.colorId = color.id();

        MinecraftServer server = player.getServer();
        if (server != null) {
            syncScoreboard(server);
            save(server);
        }
        return null;
    }

    public static String kickMember(ServerPlayerEntity player, UUID targetUuid) {
        GuildTeam team = getTeamFor(player.getUuid());
        if (team == null) return "You're not in a team.";
        if (!isLeader(team, player.getUuid())) return "Only the team leader can do that.";
        if (targetUuid == null) return "That member isn't valid.";
        if (targetUuid.equals(player.getUuid())) return "Use leave to disband the team.";

        GuildMemberData member = team.findMember(targetUuid);
        if (member == null) return "That player isn't in your team.";

        team.members.remove(member);
        INVITES.removeIf(invite -> invite.inviteeUuid.equals(targetUuid));

        MinecraftServer server = player.getServer();
        if (server != null) {
            ServerPlayerEntity kicked = server.getPlayerManager().getPlayer(targetUuid);
            if (kicked != null) {
                kicked.sendMessage(Text.literal("You were removed from " + team.name + "."), true);
            }
            syncScoreboard(server);
            save(server);
        }
        return null;
    }

    public static String setMemberStatus(ServerPlayerEntity player, String status) {
        GuildTeam team = getTeamFor(player.getUuid());
        if (team == null) return "You're not in a team.";

        status = normalizeInline(status);
        if (status.length() > STATUS_MAX_LEN) return "Status is too long.";

        GuildMemberData member = team.findMember(player.getUuid());
        if (member == null) return "You're not in a team.";

        member.name = player.getName().getString();
        member.status = status;

        MinecraftServer server = player.getServer();
        if (server != null) {
            save(server);
        }
        return null;
    }

    public static String setTeamNotes(ServerPlayerEntity player, String notes) {
        GuildTeam team = getTeamFor(player.getUuid());
        if (team == null) return "You're not in a team.";

        notes = normalizeBody(notes);
        if (notes.length() > NOTES_MAX_LEN) return "Notes are too long.";

        team.notes = notes;

        MinecraftServer server = player.getServer();
        if (server != null) {
            save(server);
        }
        return null;
    }

    public static String invitePlayer(ServerPlayerEntity inviter, String targetName) {
        GuildTeam team = getTeamFor(inviter.getUuid());
        if (team == null) return "You're not in a team.";

        targetName = normalizeInline(targetName);
        if (targetName.isEmpty()) return "Enter a player name.";

        ServerPlayerEntity target = findOnlinePlayer(inviter.getServer(), targetName);
        if (target == null) return "That player isn't online.";
        if (target.getUuid().equals(inviter.getUuid())) return "You can't invite yourself.";
        if (getTeamFor(target.getUuid()) != null) return "That player is already in a team.";

        cleanupExpiredInvites();
        INVITES.removeIf(invite -> invite.inviteeUuid.equals(target.getUuid()));

        PendingInvite invite = new PendingInvite(
                UUID.randomUUID(),
                team.id,
                team.name,
                inviter.getName().getString(),
                inviter.getUuid(),
                target.getUuid(),
                System.currentTimeMillis() + INVITE_DURATION_MS
        );
        INVITES.add(invite);

        inviter.sendMessage(Text.literal("Invitation sent to " + target.getName().getString() + "."), true);
        target.sendMessage(buildInviteMessage(invite), false);
        return null;
    }

    public static String acceptInvite(ServerPlayerEntity player, String inviteIdString) {
        cleanupExpiredInvites();

        UUID inviteId;
        try {
            inviteId = UUID.fromString(inviteIdString);
        } catch (Exception e) {
            return "That invite isn't valid.";
        }

        PendingInvite invite = null;
        for (PendingInvite entry : INVITES) {
            if (entry.id.equals(inviteId)) {
                invite = entry;
                break;
            }
        }

        if (invite == null) return "That invite expired.";
        if (!invite.inviteeUuid.equals(player.getUuid())) return "That invite isn't for you.";

        if (getTeamFor(player.getUuid()) != null) {
            INVITES.remove(invite);
            return "You're already in a team.";
        }

        GuildTeam team = null;
        for (GuildTeam entry : TEAMS) {
            if (entry.id.equals(invite.teamId)) {
                team = entry;
                break;
            }
        }

        if (team == null) {
            INVITES.remove(invite);
            return "That team no longer exists.";
        }

        team.members.add(new GuildMemberData(
                player.getUuid(),
                player.getName().getString(),
                "",
                System.currentTimeMillis()
        ));

        INVITES.remove(invite);

        MinecraftServer server = player.getServer();
        if (server != null) {
            syncScoreboard(server);
            save(server);
        }
        return null;
    }

    public static void syncScoreboard(MinecraftServer server) {
        Scoreboard scoreboard = server.getScoreboard();

        List<Team> existingTeams = new ArrayList<>(scoreboard.getTeams());
        for (Team scoreboardTeam : existingTeams) {
            if (scoreboardTeam.getName().startsWith(SCOREBOARD_TEAM_PREFIX)) {
                scoreboard.removeTeam(scoreboardTeam);
            }
        }

        for (GuildTeam team : TEAMS) {
            GuildColorOption color = GuildColorOption.byId(team.colorId);
            String scoreboardName = scoreboardName(team);
            Team scoreboardTeam = scoreboard.getTeam(scoreboardName);
            if (scoreboardTeam == null) {
                scoreboardTeam = scoreboard.addTeam(scoreboardName);
            }

            scoreboardTeam.setDisplayName(Text.literal(team.name).formatted(color.formatting()));
            scoreboardTeam.setColor(color.formatting());
            scoreboardTeam.setPrefix(Text.literal(team.prefix + " ").formatted(color.formatting()));
            scoreboardTeam.setSuffix(Text.empty());

            for (GuildMemberData member : team.members) {
                String entryName = displayName(server, member);
                if (entryName.isBlank()) continue;
                member.name = entryName;
                Team existingTeam = scoreboard.getPlayerTeam(entryName);
                if (existingTeam != null) {
                    scoreboard.removePlayerFromTeam(entryName, existingTeam);
                }
                scoreboard.addPlayerToTeam(entryName, scoreboardTeam);
            }
        }
    }

    private static boolean isLeader(GuildTeam team, UUID playerUuid) {
        return team != null && playerUuid != null && team.ownerUuid.equals(playerUuid);
    }

    private static MutableText buildInviteMessage(PendingInvite invite) {
        MutableText joinButton = Text.literal("[Join]")
                .setStyle(Style.EMPTY
                        .withColor(0xFF8AF0A8)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/oddguildaccept " + invite.id))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Join " + invite.teamName))));

        return Text.literal(invite.inviterName + " invited you to join " + invite.teamName + ". ")
                .append(joinButton);
    }

    private static ServerPlayerEntity findOnlinePlayer(MinecraftServer server, String targetName) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.getName().getString().equalsIgnoreCase(targetName)) {
                return player;
            }
        }
        return null;
    }

    private static String displayName(MinecraftServer server, GuildMemberData member) {
        ServerPlayerEntity online = server.getPlayerManager().getPlayer(member.uuid);
        if (online != null) {
            return online.getName().getString();
        }
        return member.name == null ? "" : member.name;
    }

    private static String scoreboardName(GuildTeam team) {
        String raw = team.id.toString().replace("-", "");
        return SCOREBOARD_TEAM_PREFIX + raw.substring(0, Math.min(14, raw.length()));
    }


    public static int visibleCharacterCount(String s) {
        s = normalizeInline(s);
        if (s.isEmpty()) return 0;

        BreakIterator iterator = BreakIterator.getCharacterInstance(Locale.ROOT);
        iterator.setText(s);

        int count = 0;
        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            if (end > start) {
                count++;
            }
        }
        return count;
    }

    public static boolean isSingleVisibleCharacter(String s) {
        return visibleCharacterCount(s) == 1;
    }

    private static String validatePrefix(String prefix) {
        if (prefix.isEmpty()) return "Prefix can't be empty.";
        if (prefix.length() > PREFIX_MAX_LEN) return "Prefix is too long.";
        if (!isSingleVisibleCharacter(prefix)) return "Prefix must be exactly 1 character.";
        return null;
    }

    private static void cleanupExpiredInvites() {
        long now = System.currentTimeMillis();
        Iterator<PendingInvite> it = INVITES.iterator();
        while (it.hasNext()) {
            if (it.next().expiresAt < now) {
                it.remove();
            }
        }
    }

    private static String normalizeInline(String s) {
        if (s == null) return "";
        return s.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static String normalizeBody(String s) {
        if (s == null) return "";
        return s.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static Path saveDir(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("odd_guilds");
    }

    private static Path saveFile(MinecraftServer server) {
        return saveDir(server).resolve("guilds.nbt");
    }

    private record PendingInvite(
            UUID id,
            UUID teamId,
            String teamName,
            String inviterName,
            UUID inviterUuid,
            UUID inviteeUuid,
            long expiresAt
    ) {}
}
