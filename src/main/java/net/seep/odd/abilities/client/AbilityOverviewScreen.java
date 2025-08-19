package net.seep.odd.abilities.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.seep.odd.abilities.power.Power;
import net.seep.odd.abilities.power.Powers;

import java.util.List;

public class AbilityOverviewScreen extends Screen {
    // Fallback portrait; override per power via Power.portraitTexture()
    private static final Identifier PLAYER_ICON = new Identifier("odd", "textures/gui/overview/player_icon.png");

    // scroll state for the left panel
    private int leftScroll = 0;
    private int leftContentHeight = 0;
    private int leftX, leftY, leftW, leftH; // last render bounds to know where to scroll

    public AbilityOverviewScreen() {
        super(Text.literal("Ability Overview"));
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        var mc = MinecraftClient.getInstance();
        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();

        // dim world
        ctx.fill(0, 0, w, h, 0xAA000000);

        // layout
        int margin = 24;
        int gap = 20;
        int leftWidth = 280;
        int yTop = margin + 12;
        int rightW = w - margin * 2 - leftWidth - gap;

        // current power
        String powerId = ClientPowerHolder.get();
        Power p = powerId == null ? null : Powers.get(powerId);

        // Left panel (portrait + hype text)
        drawPlayerPanel(ctx, leftX = margin, leftY = yTop, leftW = leftWidth, leftH = h - margin * 2, p);

        // Right panel (ability cards + descriptions)
        drawAbilitiesPanel(ctx, margin + leftWidth + gap, yTop, rightW, p);

        super.render(ctx, mouseX, mouseY, delta);
    }

    // -------- LEFT: Portrait + long hype description (scrollable) --------
    private void drawPlayerPanel(DrawContext ctx, int x, int y, int w, int h, Power p) {
        var mc = MinecraftClient.getInstance();

        // panel bg
        ctx.fill(x, y, x + w, y + h, 0x22000000);
        ctx.drawBorder(x, y, w, h, 0x44FFFFFF);

        // portrait (per-power if provided)
        int iconSize = 126;
        int iconX = x + 12;
        int iconY = y + 12;
        Identifier portrait = (p != null) ? p.portraitTexture() : PLAYER_ICON;
        ctx.drawTexture(portrait, iconX, iconY, 0, 0, iconSize, iconSize, iconSize, iconSize);

        // header text
        int textX = iconX + iconSize + 14;
        int lineY = iconY;

        String playerName = (mc.player != null) ? mc.player.getName().getString() : "Player";
        ctx.drawTextWithShadow(mc.textRenderer, playerName.toUpperCase(), textX, lineY, 0xFFFFFF);
        lineY += 14;

        String powerName = (p != null ? p.displayName() : "No Power");
        ctx.drawTextWithShadow(mc.textRenderer, "Oddity: " + powerName, textX, lineY, 0xFFA64D);
        lineY += 12;

        // divider
        ctx.fill(textX, lineY + 4, x + w - 12, lineY + 5, 0x33FFFFFF);
        lineY += 12;

        // long hype description (scrollable)
        int contentX = textX;
        int contentY = lineY;
        int wrapW = x + w - 12 - contentX;
        int visibleH = y + h - 12 - contentY;

        String desc = (p != null) ? p.longDescription() : "Assign a power to see your legend here.";
        String[] paras = desc.isEmpty() ? new String[0] : desc.split("\\n\\n");

        int yCursor = contentY - leftScroll;
        leftContentHeight = 0;

        for (int pi = 0; pi < paras.length; pi++) {
            List<OrderedText> lines = mc.textRenderer.wrapLines(Text.literal(paras[pi]), wrapW);
            int blockH = lines.size() * (mc.textRenderer.fontHeight + 1) + (pi == paras.length - 1 ? 0 : 8);
            leftContentHeight += blockH;

            if (yCursor + blockH >= contentY && yCursor <= contentY + visibleH) {
                int drawY = yCursor;
                for (OrderedText line : lines) {
                    if (drawY >= contentY - (mc.textRenderer.fontHeight + 1) && drawY <= contentY + visibleH) {
                        ctx.drawText(mc.textRenderer, line, contentX, drawY, 0xD8D8D8, false);
                    }
                    drawY += mc.textRenderer.fontHeight + 1;
                }
            }
            yCursor += blockH;
        }

        // scrollbar
        int maxScroll = Math.max(0, leftContentHeight - visibleH);
        if (leftScroll > maxScroll) leftScroll = maxScroll;
        if (leftScroll < 0) leftScroll = 0;

        if (maxScroll > 0 && visibleH > 0) {
            int barW = 4;
            int trackX = x + w - 6 - barW;
            int trackY = contentY;
            int trackH = visibleH;
            ctx.fill(trackX, trackY, trackX + barW, trackY + trackH, 0x22000000);

            float ratio = (float) visibleH / (float) leftContentHeight;
            int thumbH = Math.max(20, (int) (trackH * ratio));
            int thumbY = trackY + (int) ((trackH - thumbH) * (leftScroll / (float) maxScroll));
            ctx.fill(trackX, thumbY, trackX + barW, thumbY + thumbH, 0x66FFFFFF);
        }
    }

    // -------- RIGHT: Ability cards + under-card descriptions --------
    private void drawAbilitiesPanel(DrawContext ctx, int x, int y, int w, Power p) {
        var mc = MinecraftClient.getInstance();

        // title
        ctx.drawTextWithShadow(mc.textRenderer, "ABILITIES", x, y, 0xFFFFFF);

        int yCursor = y + 14;

        // primary section
        yCursor += drawAbilitySection(ctx, x, yCursor, w, "primary", p);
        yCursor += 12; // gap

        // secondary section
        yCursor += drawAbilitySection(ctx, x, yCursor, w, "secondary", p);
    }

    /** Draw one ability card + long description below it. Returns total height used. */
    private int drawAbilitySection(DrawContext ctx, int x, int y, int w, String slot, Power p) {
        int cardH = drawAbilityCard(ctx, x, y, w, slot, p);
        int descTop = y + cardH + 6;

        var mc = MinecraftClient.getInstance();
        String body = (p != null) ? p.slotLongDescription(slot) : "No power assigned.";
        int wrapW = w - 6;
        int textX = x + 3;
        int lineY = descTop;

        if (body != null && !body.isEmpty()) {
            List<OrderedText> lines = mc.textRenderer.wrapLines(Text.literal(body), wrapW);
            for (OrderedText line : lines) {
                ctx.drawText(mc.textRenderer, line, textX, lineY, 0xE0E0E0, false);
                lineY += mc.textRenderer.fontHeight + 1;
            }
        }
        return (lineY - y);
    }

    /** Draw just the card row; return its height (kept at 52). */
    private int drawAbilityCard(DrawContext ctx, int x, int y, int w, String slot, Power p) {
        var mc = MinecraftClient.getInstance();
        int h = 52;

        // card bg
        ctx.fill(x, y, x + w, y + h, 0x22000000);
        ctx.drawBorder(x, y, w, h, 0x33FFFFFF);

        // icon 32x32
        int iconX = x + 8, iconY = y + 10;
        if (p != null) {
            Identifier icon = p.iconTexture(slot);
            ctx.drawTexture(icon, iconX, iconY, 0, 0, 32, 32, 32, 32);
        } else {
            ctx.fill(iconX, iconY, iconX + 32, iconY + 32, 0x33000000);
            ctx.drawBorder(iconX, iconY, 32, 32, 0x22FFFFFF);
        }

        // title
        int textX = iconX + 42;
        String title = p != null ? p.slotTitle(slot) : "Ability";
        ctx.drawTextWithShadow(mc.textRenderer, title, textX, y + 8, 0xFFFFFFFF);

        // key badge
        String key = AbilityKeybinds.boundKeyName(slot);
        int keyW = mc.textRenderer.getWidth(key) + 8;
        int keyX = x + w - keyW - 8;
        int keyY = y + (h - 16) / 2;
        ctx.fill(keyX, keyY, keyX + keyW, keyY + 16, 0x66000000);
        ctx.drawBorder(keyX, keyY, keyW, 16, 0x55FFFFFF);
        ctx.drawTextWithShadow(mc.textRenderer, key, keyX + 4, keyY + 4, 0xFFFFFF);

        return h;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        // scroll only when cursor is over left panel
        if (mouseX >= leftX && mouseX <= leftX + leftW && mouseY >= leftY && mouseY <= leftY + leftH) {
            int step = 12; // pixels per wheel notch
            leftScroll -= (int) (amount * step);
            // clamp in next render
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }
}
