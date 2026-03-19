package net.seep.odd.client.device.social;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

@Environment(EnvType.CLIENT)
public final class DeviceMultilineTextEditor {
    private static final int PAD = 4;
    private static final int LINE_GAP = 1;

    private final TextRenderer textRenderer;
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final int maxLength;

    private String text = "";
    private int cursor = 0;
    private int scrollLine = 0;
    private boolean focused = false;
    private Text placeholder = Text.empty();

    private long blinkAt = System.currentTimeMillis();
    private boolean showCursor = true;

    public DeviceMultilineTextEditor(TextRenderer textRenderer, int x, int y, int width, int height, int maxLength) {
        this.textRenderer = textRenderer;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.maxLength = maxLength;
    }

    public void setPlaceholder(Text placeholder) {
        this.placeholder = placeholder == null ? Text.empty() : placeholder;
    }

    public void setFocused(boolean focused) {
        if (this.focused != focused) {
            this.focused = focused;
            resetBlink();
        }
    }

    public boolean isFocused() {
        return focused;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = normalize(text);
        if (this.text.length() > maxLength) {
            this.text = this.text.substring(0, maxLength);
        }
        this.cursor = MathHelper.clamp(this.cursor, 0, this.text.length());
        ensureCursorVisible();
    }

    public int length() {
        return text.length();
    }

    public int lineCount() {
        int count = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') count++;
        }
        return count;
    }

    public void tick() {
        long now = System.currentTimeMillis();
        if (now - blinkAt >= 300L) {
            blinkAt = now;
            showCursor = !showCursor;
        }
    }

    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int border = focused ? 0xFFE6F1FF : 0xFF5E6B81;
        context.fill(x - 1, y - 1, x + width + 1, y + height + 1, border);
        context.fill(x, y, x + width, y + height, 0xF0121722);

        int innerX = x + PAD;
        int innerY = y + PAD;
        int innerW = width - (PAD * 2);
        int innerH = height - (PAD * 2);

        List<VisualLine> lines = buildVisualLines(text);
        clampScroll(lines.size());

        context.enableScissor(x + 1, y + 1, x + width - 1, y + height - 1);

        int visibleLines = visibleLineCount();
        int lineHeight = lineHeight();

        if (text.isEmpty()) {
            List<OrderedText> placeholderLines = textRenderer.wrapLines(placeholder, innerW);
            int drawY = innerY;
            for (int i = 0; i < Math.min(placeholderLines.size(), visibleLines); i++) {
                context.drawTextWithShadow(textRenderer, placeholderLines.get(i), innerX, drawY, 0xFF78849B);
                drawY += lineHeight;
            }
        } else {
            for (int i = scrollLine; i < Math.min(lines.size(), scrollLine + visibleLines + 1); i++) {
                int drawY = innerY + ((i - scrollLine) * lineHeight);
                String s = lines.get(i).text;
                if (!s.isEmpty()) {
                    context.drawTextWithShadow(textRenderer, s, innerX, drawY, 0xFFE4ECFF);
                }
            }
        }

        if (focused && showCursor) {
            int lineIndex = findVisualLineIndex(lines, cursor);
            if (lineIndex >= scrollLine && lineIndex < scrollLine + visibleLines) {
                VisualLine line = lines.get(lineIndex);
                int cursorX = innerX + textRenderer.getWidth(text.substring(line.start, Math.min(cursor, line.end)));
                int cursorY = innerY + ((lineIndex - scrollLine) * lineHeight);
                context.fill(cursorX, cursorY, cursorX + 1, cursorY + textRenderer.fontHeight + 1, 0xFFFFFFFF);
            }
        }

        context.disableScissor();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        boolean inside = isInside(mouseX, mouseY);
        setFocused(inside);
        if (!inside) return false;

        List<VisualLine> lines = buildVisualLines(text);
        int clickedLine = scrollLine + MathHelper.clamp((int) ((mouseY - (y + PAD)) / lineHeight()), 0, Math.max(0, lines.size() - 1));
        clickedLine = MathHelper.clamp(clickedLine, 0, Math.max(0, lines.size() - 1));

        int localX = Math.max(0, (int) mouseX - (x + PAD));
        cursor = cursorFromLineX(lines.get(clickedLine), localX);
        resetBlink();
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!isInside(mouseX, mouseY)) return false;

        List<VisualLine> lines = buildVisualLines(text);
        int maxScroll = Math.max(0, lines.size() - visibleLineCount());
        scrollLine = MathHelper.clamp(scrollLine - (int) Math.signum(amount), 0, maxScroll);
        return true;
    }

    public boolean charTyped(char chr, int modifiers) {
        if (!focused) return false;
        if (!SharedConstants.isValidChar(chr)) return false;

        insertText(String.valueOf(chr));
        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!focused) return false;

        if (Screen.hasControlDown()) {
            if (keyCode == GLFW.GLFW_KEY_V) {
                insertText(MinecraftClient.getInstance().keyboard.getClipboard());
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_A) {
                cursor = text.length();
                ensureCursorVisible();
                resetBlink();
                return true;
            }
        }

        switch (keyCode) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                insertText("\n");
                return true;
            }
            case GLFW.GLFW_KEY_TAB -> {
                insertText("    ");
                return true;
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (cursor > 0) {
                    text = text.substring(0, cursor - 1) + text.substring(cursor);
                    cursor--;
                    ensureCursorVisible();
                    resetBlink();
                }
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (cursor < text.length()) {
                    text = text.substring(0, cursor) + text.substring(cursor + 1);
                    ensureCursorVisible();
                    resetBlink();
                }
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                if (cursor > 0) cursor--;
                ensureCursorVisible();
                resetBlink();
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                if (cursor < text.length()) cursor++;
                ensureCursorVisible();
                resetBlink();
                return true;
            }
            case GLFW.GLFW_KEY_UP -> {
                moveVertical(-1);
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                moveVertical(1);
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                cursor = lineStart(cursor);
                ensureCursorVisible();
                resetBlink();
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                cursor = lineEnd(cursor);
                ensureCursorVisible();
                resetBlink();
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void insertText(String incoming) {
        incoming = normalize(incoming);
        if (incoming.isEmpty()) return;

        int spaceLeft = maxLength - text.length();
        if (spaceLeft <= 0) return;

        if (incoming.length() > spaceLeft) {
            incoming = incoming.substring(0, spaceLeft);
        }

        text = text.substring(0, cursor) + incoming + text.substring(cursor);
        cursor += incoming.length();
        ensureCursorVisible();
        resetBlink();
    }

    private void moveVertical(int dir) {
        List<VisualLine> lines = buildVisualLines(text);
        if (lines.isEmpty()) return;

        int currentLine = findVisualLineIndex(lines, cursor);
        int targetLine = MathHelper.clamp(currentLine + dir, 0, lines.size() - 1);

        VisualLine current = lines.get(currentLine);
        int xPos = textRenderer.getWidth(text.substring(current.start, Math.min(cursor, current.end)));

        cursor = cursorFromLineX(lines.get(targetLine), xPos);
        ensureCursorVisible();
        resetBlink();
    }

    private int cursorFromLineX(VisualLine line, int xPos) {
        if (line.start == line.end) return line.start;

        int bestCursor = line.start;
        int bestDiff = Integer.MAX_VALUE;

        for (int i = line.start; i <= line.end; i++) {
            int width = textRenderer.getWidth(text.substring(line.start, i));
            int diff = Math.abs(width - xPos);
            if (diff <= bestDiff) {
                bestDiff = diff;
                bestCursor = i;
            }
        }

        return bestCursor;
    }

    private int lineStart(int index) {
        int i = MathHelper.clamp(index, 0, text.length());
        int prev = text.lastIndexOf('\n', Math.max(0, i - 1));
        return prev == -1 ? 0 : prev + 1;
    }

    private int lineEnd(int index) {
        int i = MathHelper.clamp(index, 0, text.length());
        int next = text.indexOf('\n', i);
        return next == -1 ? text.length() : next;
    }

    private void ensureCursorVisible() {
        List<VisualLine> lines = buildVisualLines(text);
        int lineIndex = findVisualLineIndex(lines, cursor);
        int visible = visibleLineCount();

        if (lineIndex < scrollLine) {
            scrollLine = lineIndex;
        } else if (lineIndex >= scrollLine + visible) {
            scrollLine = lineIndex - visible + 1;
        }

        clampScroll(lines.size());
    }

    private void clampScroll(int totalLines) {
        int maxScroll = Math.max(0, totalLines - visibleLineCount());
        scrollLine = MathHelper.clamp(scrollLine, 0, maxScroll);
    }

    private int visibleLineCount() {
        return Math.max(1, (height - (PAD * 2)) / lineHeight());
    }

    private int lineHeight() {
        return textRenderer.fontHeight + LINE_GAP;
    }

    private int findVisualLineIndex(List<VisualLine> lines, int cursor) {
        if (lines.isEmpty()) return 0;
        for (int i = 0; i < lines.size(); i++) {
            VisualLine line = lines.get(i);
            if (cursor >= line.start && cursor <= line.end) {
                return i;
            }
        }
        return lines.size() - 1;
    }

    private List<VisualLine> buildVisualLines(String raw) {
        List<VisualLine> out = new ArrayList<>();
        int maxWidth = width - (PAD * 2);

        if (raw.isEmpty()) {
            out.add(new VisualLine(0, 0, ""));
            return out;
        }

        int lineStart = 0;
        while (lineStart <= raw.length()) {
            int newline = raw.indexOf('\n', lineStart);
            int logicalEnd = newline == -1 ? raw.length() : newline;

            if (lineStart == logicalEnd) {
                out.add(new VisualLine(lineStart, lineStart, ""));
            } else {
                int segStart = lineStart;
                while (segStart < logicalEnd) {
                    int segEnd = fitSegment(raw, segStart, logicalEnd, maxWidth);
                    out.add(new VisualLine(segStart, segEnd, raw.substring(segStart, segEnd)));
                    segStart = segEnd;
                }
            }

            if (newline == -1) break;

            lineStart = newline + 1;
            if (lineStart == raw.length()) {
                out.add(new VisualLine(lineStart, lineStart, ""));
                break;
            }
        }

        if (out.isEmpty()) {
            out.add(new VisualLine(0, 0, ""));
        }

        return out;
    }

    private int fitSegment(String raw, int start, int logicalEnd, int maxWidth) {
        int end = start;
        int lastBreak = -1;

        while (end < logicalEnd) {
            String next = raw.substring(start, end + 1);
            if (textRenderer.getWidth(next) > maxWidth) {
                break;
            }

            char c = raw.charAt(end);
            if (Character.isWhitespace(c)) {
                lastBreak = end + 1;
            }

            end++;
        }

        if (end == start) {
            return Math.min(logicalEnd, start + 1);
        }

        if (end < logicalEnd && lastBreak > start) {
            return lastBreak;
        }

        return end;
    }

    private boolean isInside(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private void resetBlink() {
        blinkAt = System.currentTimeMillis();
        showCursor = true;
    }

    private static String normalize(String s) {
        return s == null ? "" : s.replace("\r\n", "\n").replace('\r', '\n');
    }

    private record VisualLine(int start, int end, String text) {}
}