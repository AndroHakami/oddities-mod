package net.seep.odd.client.device.guild;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.seep.odd.Oddities;
import net.seep.odd.client.device.DeviceHomeScreen;
import net.seep.odd.device.guild.GuildColorOption;
import net.seep.odd.device.guild.GuildMemberData;
import net.seep.odd.device.guild.GuildNetworking;
import net.seep.odd.device.guild.GuildTeam;

@Environment(EnvType.CLIENT)
public final class DeviceGuildScreen extends Screen {
    private static final Identifier HOME_BG = new Identifier(Oddities.MOD_ID, "textures/gui/device/home_bg.png");
    private static final Identifier HOME_OVERLAY = new Identifier(Oddities.MOD_ID, "textures/gui/device/home_overlay.png");
    private static final Identifier ICON_HOME = new Identifier(Oddities.MOD_ID, "textures/gui/device/social/top/home.png");
    private static final Identifier ICON_REFRESH = new Identifier(Oddities.MOD_ID, "textures/gui/device/social/top/refresh.png");
    private static final Identifier ICON_MANAGE = new Identifier(Oddities.MOD_ID, "textures/gui/device/guild/top/manage.png");
    private static final Identifier ICON_LEAVE = new Identifier(Oddities.MOD_ID, "textures/gui/device/guild/top/leave.png");

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final List<ClickZone> zones = new ArrayList<>();

    private double scroll = 0.0;
    private int maxScroll = 0;

    public DeviceGuildScreen() {
        super(Text.literal("Guilds"));
    }

    @Override
    protected void init() {
        requestSync();
    }

    private void requestSync() {
        ClientPlayNetworking.send(
                GuildNetworking.C2S_REQUEST_SYNC,
                net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create()
        );
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);

        int left = (this.width - DeviceHomeScreen.GUI_W) / 2;
        int top = (this.height - DeviceHomeScreen.GUI_H) / 2;

        zones.clear();

        context.fill(left - 5, top - 5, left + DeviceHomeScreen.GUI_W + 5, top + DeviceHomeScreen.GUI_H + 5, 0x66000000);
        context.drawTexture(HOME_BG, left, top, 0, 0, DeviceHomeScreen.GUI_W, DeviceHomeScreen.GUI_H, DeviceHomeScreen.GUI_W, DeviceHomeScreen.GUI_H);

        renderTopButtons(context, left, top, mouseX, mouseY);

        int contentX = left + 14;
        int contentY = top + 40;
        int contentW = DeviceHomeScreen.GUI_W - 28;
        int contentH = DeviceHomeScreen.GUI_H - 54;
        context.fill(contentX, contentY, contentX + contentW, contentY + contentH, 0x8C101622);

        GuildTeam team = GuildClientCache.team();
        int drawY = contentY + 8 - (int) scroll;
        int totalH;

        context.enableScissor(contentX, contentY, contentX + contentW, contentY + contentH);

        if (team == null) {
            totalH = renderEmptyState(context, contentX + 8, drawY, contentW - 16, mouseX, mouseY);
        } else {
            totalH = renderTeamState(context, team, contentX + 8, drawY, contentW - 16, mouseX, mouseY);
        }

        context.disableScissor();

        maxScroll = Math.max(0, totalH - contentH + 12);
        scroll = MathHelper.clamp(scroll, 0.0, maxScroll);

        context.drawTexture(HOME_OVERLAY, left, top, 0, 0, DeviceHomeScreen.GUI_W, DeviceHomeScreen.GUI_H, DeviceHomeScreen.GUI_W, DeviceHomeScreen.GUI_H);

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderTopButtons(DrawContext context, int left, int top, int mouseX, int mouseY) {
        int homeX = left + 18;
        int homeY = top + 14;
        int refreshX = left + DeviceHomeScreen.GUI_W - 18 - 22;
        int refreshY = top + 14;

        renderIconButton(context, ICON_HOME, homeX, homeY, 22, mouseX, mouseY);
        renderIconButton(context, ICON_REFRESH, refreshX, refreshY, 22, mouseX, mouseY);

        zones.add(new ClickZone(homeX, homeY, homeX + 22, homeY + 22, () -> {
            if (this.client != null) this.client.setScreen(new DeviceHomeScreen());
        }));

        zones.add(new ClickZone(refreshX, refreshY, refreshX + 22, refreshY + 22, this::requestSync));

        GuildTeam team = GuildClientCache.team();
        if (team != null) {
            boolean leader = isLeader(team);
            int iconSize = 22;
            int iconGap = 6;
            int actionCount = leader ? 2 : 1;
            int totalW = (actionCount * iconSize) + ((actionCount - 1) * iconGap);
            int startX = left + (DeviceHomeScreen.GUI_W / 2) - (totalW / 2);
            int actionY = top + 14;

            if (leader) {
                int manageX = startX;
                renderIconButton(context, ICON_MANAGE, manageX, actionY, iconSize, mouseX, mouseY);
                zones.add(new ClickZone(manageX, actionY, manageX + iconSize, actionY + iconSize, () -> {
                    if (this.client != null) {
                        this.client.setScreen(new DeviceGuildManageScreen(this, team));
                    }
                }));
                startX += iconSize + iconGap;
            }

            int leaveX = startX;
            renderIconButton(context, ICON_LEAVE, leaveX, actionY, iconSize, mouseX, mouseY);
            zones.add(new ClickZone(leaveX, actionY, leaveX + iconSize, actionY + iconSize, () -> openLeaveConfirm(team)));
        } else {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Guilds"), left + (DeviceHomeScreen.GUI_W / 2), top + 20, 0xFFEAF2FF);
        }
    }

    private int renderEmptyState(DrawContext context, int x, int y, int width, int mouseX, int mouseY) {
        int cardH = 116;

        context.fill(x, y, x + width, y + cardH, 0xD81A2230);
        context.fill(x, y, x + width, y + 1, 0x55E8F0FF);

        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("No team yet"), x + (width / 2), y + 18, 0xFFF2F7FF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Create one to start sharing statuses and notes."), x + (width / 2), y + 34, 0xFFB9CBE8);

        int btnX = x + (width / 2) - 42;
        int btnY = y + 60;
        renderTextButton(context, "Create Team", btnX, btnY, 84, 20, mouseX, mouseY);
        zones.add(new ClickZone(btnX, btnY, btnX + 84, btnY + 20, () -> {
            if (this.client != null) {
                this.client.setScreen(new DeviceGuildCreateScreen(this));
            }
        }));

        return cardH + 16;
    }

    private int renderTeamState(DrawContext context, GuildTeam team, int x, int y, int width, int mouseX, int mouseY) {
        GuildColorOption color = GuildColorOption.byId(team.colorId);
        boolean leader = isLeader(team);

        int headerH = 78;
        context.fill(x, y, x + width, y + headerH, 0xD81A2230);
        context.fill(x, y, x + width, y + 1, color.color());

        int onlineCount = 0;
        for (GuildMemberData member : team.members) {
            if (member.online) onlineCount++;
        }

        context.fill(x + 10, y + 12, x + 22, y + 24, color.color());
        context.drawTextWithShadow(this.textRenderer, Text.literal(team.name), x + 28, y + 12, 0xFFF2F7FF);
        context.drawTextWithShadow(this.textRenderer, Text.literal(team.prefix), x + 28, y + 23, color.color());
        context.drawTextWithShadow(this.textRenderer, Text.literal("Created " + DATE_FMT.format(Instant.ofEpochMilli(team.createdAt).atZone(ZoneId.systemDefault()))),
                x + 10, y + 40, 0xFFB8C9E8);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Online " + onlineCount + "/" + team.members.size()),
                x + width - 68, y + 12, 0xFFEAF2FF);

        int actionY = y + 54;
        int actionW = 58;
        int gap = 6;
        int total = (actionW * 3) + (gap * 2);
        int actionX = x + (width - total) / 2;

        renderTextButton(context, "Status", actionX, actionY, actionW, 18, mouseX, mouseY);
        renderTextButton(context, "Notes", actionX + actionW + gap, actionY, actionW, 18, mouseX, mouseY);
        renderTextButton(context, "Invite", actionX + (actionW + gap) * 2, actionY, actionW, 18, mouseX, mouseY);

        zones.add(new ClickZone(actionX, actionY, actionX + actionW, actionY + 18, () -> openStatusEditor(team)));
        zones.add(new ClickZone(actionX + actionW + gap, actionY, actionX + actionW + gap + actionW, actionY + 18, () -> {
            if (this.client != null) {
                this.client.setScreen(new DeviceGuildNotesEditScreen(this, team.notes));
            }
        }));
        zones.add(new ClickZone(actionX + (actionW + gap) * 2, actionY, actionX + (actionW + gap) * 2 + actionW, actionY + 18, () -> {
            if (this.client != null) {
                this.client.setScreen(new DeviceGuildInviteScreen(this));
            }
        }));

        int cursorY = y + headerH + 8;

        if (!team.notes.isBlank()) {
            int notesH = measureWrapped(team.notes, width - 20, 4) + 24;
            context.fill(x, cursorY, x + width, cursorY + notesH, 0xC3161F2D);
            context.drawTextWithShadow(this.textRenderer, Text.literal("Team Notes"), x + 10, cursorY + 8, 0xFFEAF2FF);
            drawWrappedLimit(context, team.notes, x + 10, cursorY + 20, width - 20, 4, 0xFFD8E4F8);
            cursorY += notesH + 8;
        }

        for (GuildMemberData member : team.members) {
            boolean memberIsLeader = team.ownerUuid.equals(member.uuid);
            boolean canKick = leader && !member.uuid.equals(team.ownerUuid);
            int cardH = measureMemberHeight(member, width) + (canKick ? 6 : 0);
            context.fill(x, cursorY, x + width, cursorY + cardH, member.online ? 0xD3212E43 : 0xCC171D2A);
            context.fill(x, cursorY, x + width, cursorY + 1, member.online ? color.color() : 0x55687890);

            int outlineColor = member.online ? 0xFF78EE9B : 0xFF7F8797;
            renderFaceOutlined(context, member.uuid, x + 10, cursorY + 8, 12, outlineColor);

            int nameX = x + 28;
            if (memberIsLeader) {
                context.drawTextWithShadow(this.textRenderer, Text.literal("♛"), nameX, cursorY + 8, 0xFFFFD46A);
                nameX += 10;
            }
            context.drawTextWithShadow(this.textRenderer, Text.literal(member.name), nameX, cursorY + 8, 0xFFF2F7FF);
            context.drawTextWithShadow(this.textRenderer, Text.literal(member.online ? "Online" : "Offline"), x + width - 40, cursorY + 8, member.online ? 0xFF8FF1A7 : 0xFF9FAEC8);

            String status = member.status == null || member.status.isBlank() ? "No status set." : member.status;
            drawWrapped(context, status, x + 10, cursorY + 24, width - 20, 0xFFD3DDF2);

            if (canKick) {
                int kickW = 42;
                int kickH = 16;
                int kickX = x + width - kickW - 10;
                int kickY = cursorY + cardH - kickH - 8;
                renderTextButton(context, "Kick", kickX, kickY, kickW, kickH, mouseX, mouseY);
                zones.add(new ClickZone(kickX, kickY, kickX + kickW, kickY + kickH, () -> openKickConfirm(member)));
            }

            cursorY += cardH + 6;
        }

        return cursorY - y + 8;
    }

    private void openStatusEditor(GuildTeam team) {
        String current = "";
        if (this.client != null && this.client.player != null) {
            for (GuildMemberData member : team.members) {
                if (member.uuid.equals(this.client.player.getUuid())) {
                    current = member.status;
                    break;
                }
            }
            this.client.setScreen(new DeviceGuildStatusEditScreen(this, current));
        }
    }

    private void openLeaveConfirm(GuildTeam team) {
        if (this.client == null) return;
        boolean leader = isLeader(team);
        String body = leader
                ? "Leaving as the leader will disband the whole team."
                : "Leave your current team?";
        this.client.setScreen(new DeviceGuildConfirmScreen(
                this,
                Text.literal("Leave Team"),
                body,
                "Leave",
                () -> {
                    ClientPlayNetworking.send(GuildNetworking.C2S_LEAVE_TEAM, net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create());
                    if (this.client != null) {
                        this.client.setScreen(new DeviceGuildScreen());
                    }
                }
        ));
    }

    private void openKickConfirm(GuildMemberData member) {
        if (this.client == null) return;
        this.client.setScreen(new DeviceGuildConfirmScreen(
                this,
                Text.literal("Kick Member"),
                "Remove " + member.name + " from the team?",
                "Kick",
                () -> {
                    var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
                    buf.writeUuid(member.uuid);
                    ClientPlayNetworking.send(GuildNetworking.C2S_KICK_MEMBER, buf);
                    if (this.client != null) {
                        this.client.setScreen(new DeviceGuildScreen());
                    }
                }
        ));
    }

    private boolean isLeader(GuildTeam team) {
        return this.client != null && this.client.player != null && team.ownerUuid.equals(this.client.player.getUuid());
    }

    private int measureMemberHeight(GuildMemberData member, int width) {
        String status = member.status == null || member.status.isBlank() ? "No status set." : member.status;
        return 30 + measureWrapped(status, width - 20, Integer.MAX_VALUE) + 8;
    }

    private int measureWrapped(String text, int width, int maxLines) {
        List<OrderedText> lines = this.textRenderer.wrapLines(Text.literal(text), width);
        return Math.max(1, Math.min(maxLines, lines.size())) * 9;
    }

    private int drawWrapped(DrawContext context, String text, int x, int y, int width, int color) {
        List<OrderedText> lines = this.textRenderer.wrapLines(Text.literal(text), width);
        for (OrderedText line : lines) {
            context.drawTextWithShadow(this.textRenderer, line, x, y, color);
            y += 9;
        }
        return y;
    }

    private void drawWrappedLimit(DrawContext context, String text, int x, int y, int width, int maxLines, int color) {
        List<OrderedText> lines = this.textRenderer.wrapLines(Text.literal(text), width);
        int count = Math.min(maxLines, lines.size());
        for (int i = 0; i < count; i++) {
            context.drawTextWithShadow(this.textRenderer, lines.get(i), x, y, color);
            y += 9;
        }
        if (lines.size() > maxLines) {
            context.drawTextWithShadow(this.textRenderer, Text.literal("..."), x, y - 9, 0xFF9FB0CC);
        }
    }

    private void renderFaceOutlined(DrawContext context, UUID uuid, int x, int y, int size, int outlineColor) {
        context.fill(x - 1, y - 1, x + size + 1, y + size + 1, outlineColor);
        context.fill(x, y, x + size, y + size, 0xFF101622);
        renderFace(context, uuid, x, y, size);
    }

    private void renderFace(DrawContext context, UUID uuid, int x, int y, int size) {
        Identifier skin = DefaultSkinHelper.getTexture(uuid);
        if (MinecraftClient.getInstance().getNetworkHandler() != null) {
            PlayerListEntry entry = MinecraftClient.getInstance().getNetworkHandler().getPlayerListEntry(uuid);
            if (entry != null) {
                skin = entry.getSkinTexture();
            }
        }

        context.drawTexture(skin, x, y, size, size, 8.0f, 8.0f, 8, 8, 64, 64);
        context.drawTexture(skin, x, y, size, size, 40.0f, 8.0f, 8, 8, 64, 64);
    }

    private void renderTextButton(DrawContext context, String label, int x, int y, int w, int h, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        context.fill(x, y, x + w, y + h, hovered ? 0xA0324764 : 0x80233448);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(label), x + (w / 2), y + ((h - 8) / 2), 0xFFF2F7FF);
    }

    private void renderIconButton(DrawContext context, Identifier texture, int x, int y, int size, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + size && mouseY >= y && mouseY <= y + size;
        float hover = hovered ? 1.0f : 0.0f;
        float time = (System.currentTimeMillis() / 90.0f);
        float scale = 1.0f + ((float) Math.sin(time * 0.10f) * 0.02f) + (hover * 0.18f);

        int cx = x + (size / 2);
        int cy = y + (size / 2);

        if (hovered) {
            context.fill(cx - (size / 2) - 2, cy - (size / 2) - 2, cx + (size / 2) + 2, cy + (size / 2) + 2, 0x48D4E5FF);
        }

        context.getMatrices().push();
        context.getMatrices().translate(cx, cy, 0.0f);
        context.getMatrices().scale((size / 24.0f) * scale, (size / 24.0f) * scale, 1.0f);
        context.getMatrices().translate(-12.0f, -12.0f, 0.0f);
        context.drawTexture(texture, 0, 0, 0, 0, 24, 24, 24, 24);
        context.getMatrices().pop();
    }

    private void playClickSound() {
        if (this.client == null || this.client.player == null) return;
        this.client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.40f, 1.00f);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (ClickZone zone : zones) {
                if (zone.contains(mouseX, mouseY)) {
                    playClickSound();
                    zone.action.run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        scroll = MathHelper.clamp(scroll - amount * 18.0, 0.0, maxScroll);
        return true;
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(new DeviceHomeScreen());
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private record ClickZone(int x1, int y1, int x2, int y2, Runnable action) {
        boolean contains(double x, double y) {
            return x >= x1 && x <= x2 && y >= y1 && y <= y2;
        }
    }
}
