package net.seep.odd.abilities.tamer.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Party Manager + Radial Wheel screens. */
public final class TamerScreens {
    private TamerScreens() {}

    /* ===== Party Manager ===== */
    public static void openPartyScreen(NbtCompound payload) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        mc.setScreen(new PartyScreen(payload));
    }

    static final class PartyScreen extends Screen {
        private final List<NbtCompound> members = new ArrayList<>();
        private int selected = -1;
        private TextFieldWidget nameField;
        private ButtonWidget renameBtn;

        protected PartyScreen(NbtCompound payload) {
            super(Text.literal("Party Manager"));
            NbtList arr = payload.getList("party", NbtCompound.COMPOUND_TYPE);
            for (int i = 0; i < arr.size(); i++) members.add(arr.getCompound(i));
        }

        @Override
        protected void init() {
            int w = this.width, h = this.height;
            int left = w / 2 - 120;
            int top  = h / 2 - 80;

            nameField = new TextFieldWidget(textRenderer, left, top + 120, 160, 20, Text.literal("Name"));
            nameField.setMaxLength(64);
            addSelectableChild(nameField);

            renameBtn = ButtonWidget.builder(Text.literal("Rename"), b -> {
                if (selected >= 0 && selected < members.size()) {
                    String newName = nameField.getText();
                    net.seep.odd.abilities.net.TamerNet.sendRename(selected, newName);
                }
            }).dimensions(left + 170, top + 120, 70, 20).build();
            addDrawableChild(renameBtn);

            super.init();
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            renderBackground(context);

            int left = this.width / 2 - 120;
            int top  = this.height / 2 - 80;

            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Party Manager"),
                    this.width / 2, top - 14, 0xFFFFFF);

            int rowH = 18;
            for (int i = 0; i < members.size(); i++) {
                int y  = top + i * rowH;
                int bg = (i == selected) ? 0x44FFFFFF : 0x22000000;
                context.fill(left, y, left + 240, y + rowH, bg);

                NbtCompound m = members.get(i);
                String nick = m.getString("nick");
                if (nick == null || nick.isEmpty()) nick = m.getString("type");
                int lv = m.getInt("lv");

                context.drawTextWithShadow(this.textRenderer, nick, left + 6, y + 5, 0xE0E0E0);

                String lvl = "Lv." + lv;
                context.drawTextWithShadow(this.textRenderer, lvl,
                        left + 240 - this.textRenderer.getWidth(lvl) - 6, y + 5, 0xA0FFC0);
            }

            super.render(context, mouseX, mouseY, delta);
            nameField.render(context, mouseX, mouseY, delta);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int left = this.width / 2 - 120;
            int top  = this.height / 2 - 80;
            int rowH = 18;

            for (int i = 0; i < members.size(); i++) {
                int y = top + i * rowH;
                if (mouseX >= left && mouseX <= left + 240 && mouseY >= y && mouseY <= y + rowH) {
                    selected = i;
                    String def = members.get(i).getString("nick");
                    if (def == null || def.isEmpty()) def = members.get(i).getString("type");
                    nameField.setText(def);
                    break;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override public boolean shouldCloseOnEsc() { return true; }
    }

    /* ===== Radial Wheel ===== */
    public static void openWheelScreen(NbtCompound payload) {
        MinecraftClient mc = MinecraftClient.getInstance(); if (mc == null) return;
        mc.setScreen(new WheelScreen(payload));
    }

    static final class WheelScreen extends Screen {
        private final List<NbtCompound> members = new ArrayList<>();
        private int hovered = -1;

        protected WheelScreen(NbtCompound payload) {
            super(Text.literal("Choose Companion"));
            NbtList arr = payload.getList("party", NbtCompound.COMPOUND_TYPE);
            for (int i = 0; i < arr.size(); i++) members.add(arr.getCompound(i));
        }

        @Override public boolean shouldPause() { return false; }

        @Override
        public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
            renderBackground(ctx);
            ctx.fill(0, 0, width, height, 0x88000000);

            int cx = width / 2, cy = height / 2;
            int R  = 78;
            hovered = -1;

            int n = Math.max(1, members.size());
            for (int i = 0; i < n; i++) {
                double ang = (-Math.PI / 2) + (2 * Math.PI) * i / n;
                int ix = cx + (int)(R * Math.cos(ang));
                int iy = cy + (int)(R * Math.sin(ang));

                int dx = mouseX - ix, dy = mouseY - iy;
                boolean hot = (dx*dx + dy*dy) <= (40*40);
                if (hot) hovered = i;

                int box = hot ? 0x66FFFFFF : 0x33000000;
                ctx.fill(ix - 36, iy - 12, ix + 36, iy + 12, box);

                NbtCompound m = members.get(i);
                String nick = m.getString("nick");
                if (nick == null || nick.isEmpty()) nick = m.getString("type");
                int lv = m.getInt("lv");
                String label = nick + "  Lv." + lv;

                int tw = textRenderer.getWidth(label);
                ctx.drawTextWithShadow(textRenderer, label, ix - tw/2, iy - 9, hot ? 0xFFFFFF : 0xD0D0D0);
            }

            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Click to summon • Esc to cancel"),
                    cx, cy + R + 24, 0xA0E0FF);

            super.render(ctx, mouseX, mouseY, delta);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (hovered >= 0 && hovered < members.size()) {
                net.seep.odd.abilities.net.TamerNet.sendSummonSelect(hovered);
                close();
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
    }
}
