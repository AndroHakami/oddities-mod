package net.seep.odd.abilities.tamer.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Party Manager + Radial Wheel + Status page (patched). */
public final class TamerScreens {
    private TamerScreens() {}

    /* ===== Party Manager ===== */
    public static void openPartyScreen(NbtCompound payload) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        mc.setScreen(new PartyScreen(payload));
    }
    public static void openCommandWheel(boolean hasActive, int modeOrdinal) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        mc.setScreen(new WheelScreen.CommandWheelScreen(hasActive, modeOrdinal));
    }

    /* ---------------- Party ---------------- */
    static final class PartyScreen extends Screen {
        private final List<NbtCompound> members = new ArrayList<>();
        private int selected = -1;

        private TextFieldWidget nameField;
        private ButtonWidget renameBtn;
        private ButtonWidget statusBtn, healBtn, kickBtn;

        protected PartyScreen(NbtCompound payload) {
            super(Text.literal("Party Manager"));
            NbtList arr = payload.getList("party", NbtCompound.COMPOUND_TYPE);
            for (int i = 0; i < arr.size(); i++) members.add(arr.getCompound(i));
        }

        @Override
        protected void init() {
            int w = this.width, h = this.height;
            int left = w / 2 - 120;
            int top  = h / 2 - 90;

            nameField = new TextFieldWidget(textRenderer, left, top + 140, 160, 20, Text.literal("Name"));
            nameField.setMaxLength(64);
            addDrawableChild(nameField);

            renameBtn = ButtonWidget.builder(Text.literal("Rename"), b -> {
                if (selected >= 0 && selected < members.size()) {
                    String newName = nameField.getText();
                    net.seep.odd.abilities.net.TamerNet.sendRename(selected, newName);
                }
            }).dimensions(left + 170, top + 140, 70, 20).build();
            addDrawableChild(renameBtn);

            statusBtn = ButtonWidget.builder(Text.literal("Status"), b -> {
                if (selected >= 0 && selected < members.size()) {
                    MinecraftClient.getInstance().setScreen(new StatusScreen(members.get(selected)));
                }
            }).dimensions(left, top + 170, 70, 20).build();
            healBtn = ButtonWidget.builder(Text.literal("Heal"), b -> {
                if (selected >= 0 && selected < members.size()) {
                    net.seep.odd.abilities.net.TamerNet.sendHeal(selected);
                }
            }).dimensions(left + 80, top + 170, 60, 20).build();
            kickBtn = ButtonWidget.builder(Text.literal("Kick"), b -> {
                if (selected >= 0 && selected < members.size()) {
                    net.seep.odd.abilities.net.TamerNet.sendKickRequest(selected);
                }
            }).dimensions(left + 150, top + 170, 60, 20).build();

            addDrawableChild(statusBtn);
            addDrawableChild(healBtn);
            addDrawableChild(kickBtn);

            boolean hasAny = !members.isEmpty();
            if (hasAny) {
                selected = 0;
                String def = members.get(0).getString("nick");
                if (def == null || def.isEmpty()) def = members.get(0).getString("type");
                nameField.setText(def);
            }
            setActionButtonsEnabled(hasAny);

            super.init();
        }

        private void setActionButtonsEnabled(boolean on) {
            if (statusBtn != null) statusBtn.active = on;
            if (healBtn   != null) healBtn.active   = on;
            if (kickBtn   != null) kickBtn.active   = on;
            if (renameBtn != null) renameBtn.active = on;
        }

        @Override
        public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
            renderBackground(ctx);

            int left = this.width / 2 - 120;
            int top  = this.height / 2 - 90;

            ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Party Manager"),
                    this.width / 2, top - 16, 0xFFFFFF);

            int rowH = 20;
            for (int i = 0; i < members.size(); i++) {
                int y  = top + i * rowH;
                int bg = (i == selected) ? 0x44FFFFFF : 0x22000000;
                ctx.fill(left, y, left + 240, y + rowH, bg);

                NbtCompound m = members.get(i);

                // icon (if present)
                if (m.contains("icon", 8)) {
                    Identifier iconId = new Identifier(m.getString("icon"));
                    ctx.drawTexture(iconId, left + 3, y + 2, 0, 0, 16, 16, 16, 16);
                }

                String nick = m.getString("nick");
                if (nick == null || nick.isEmpty()) nick = m.getString("type");
                int lv = m.getInt("lv");

                ctx.drawTextWithShadow(this.textRenderer, nick, left + 22, y + 6, 0xE0E0E0);

                String lvl = "Lv." + lv;
                ctx.drawTextWithShadow(this.textRenderer, lvl,
                        left + 240 - this.textRenderer.getWidth(lvl) - 6, y + 6, 0xA0FFC0);
            }

            super.render(ctx, mouseX, mouseY, delta);
            nameField.render(ctx, mouseX, mouseY, delta);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            boolean handledByWidgets = super.mouseClicked(mouseX, mouseY, button);

            int left = this.width / 2 - 120;
            int top  = this.height / 2 - 90;
            int rowH = 20;

            boolean picked = false;
            for (int i = 0; i < members.size(); i++) {
                int y = top + i * rowH;
                if (mouseX >= left && mouseX <= left + 240 && mouseY >= y && mouseY <= y + rowH) {
                    selected = i;
                    String def = members.get(i).getString("nick");
                    if (def == null || def.isEmpty()) def = members.get(i).getString("type");
                    nameField.setText(def);
                    picked = true;
                    break;
                }
            }
            setActionButtonsEnabled(picked || selected >= 0);
            return handledByWidgets || picked;
        }

        @Override public boolean shouldCloseOnEsc() { return true; }
    }

    /* ===== Status Screen (drag to rotate, scroll to zoom; shows accurate HP & moves) ===== */
    // inside net.seep.odd.abilities.tamer.client.TamerScreens
// replace the whole StatusScreen class with this:

    static final class StatusScreen extends Screen {
        private final NbtCompound member;
        private LivingEntity preview;

        // interactive view state
        private boolean rotating = false;
        private float rotX = 20f;
        private float rotY = -5f;
        private float zoom = 1.0f;     // 0.6 .. 2.0
        private int lastMx, lastMy;

        // model box rect (for hit-testing)
        private int boxL, boxT, boxR, boxB;

        protected StatusScreen(NbtCompound member) {
            super(Text.literal("Status"));
            this.member = member.copy();
        }

        @Override
        protected void init() {
            var mc = MinecraftClient.getInstance();
            if (mc != null && mc.world != null) {
                try {
                    Identifier id = new Identifier(member.getString("type"));
                    EntityType<?> type = Registries.ENTITY_TYPE.get(id);
                    var e = type.create(mc.world);
                    if (e instanceof LivingEntity le) {
                        this.preview = le;
                        le.setYaw(210f);
                        le.setPitch(0f);

                        // NEW: apply the same level scaling as the server so numbers match
                        net.seep.odd.abilities.tamer.PartyMember pm = net.seep.odd.abilities.tamer.PartyMember.fromNbt(member);
                        net.seep.odd.abilities.tamer.TamerStats.applyOnSpawn(le, pm);
                    }
                } catch (Exception ignored) {}
            }
            super.init();
        }

        @Override
        public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
            renderBackground(ctx);

            final int cx = width / 2;
            final int top = height / 2 - 110;

            // title
            String name = member.getString("nick");
            if (name == null || name.isEmpty()) name = member.getString("type");
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(name), cx, top - 18, 0xFFFFFF);

            /* ---- Left: big preview box ---- */
            int panelW = 260, panelH = 230;
            boxL = cx - panelW - 12;
            boxR = boxL + panelW;
            boxT = top;
            boxB = boxT + panelH;

            ctx.fill(boxL, boxT, boxR, boxB, 0x66000000);
            int border = 0xA0FFFFFF;
            ctx.fill(boxL, boxT, boxR, boxT + 1, border);
            ctx.fill(boxL, boxB - 1, boxR, boxB, border);
            ctx.fill(boxL, boxT, boxL + 1, boxB, border);
            ctx.fill(boxR - 1, boxT, boxR, boxB, border);

            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("drag to rotate • scroll to zoom"),
                    (boxL + boxR) / 2, boxB - 12, 0xC0E0FF);

            if (preview != null) {
                int renderX = (boxL + boxR) / 2;
                int renderY = boxT + (int)(panelH * 0.78f);

                float h = Math.max(1.0f, preview.getHeight());
                int base = (int)(110f / h);
                int size = Math.max(20, Math.min(220, Math.round(base * zoom)));

                InventoryScreen.drawEntity(ctx, renderX, renderY, size, -rotX, -rotY, preview);
            }

            /* ---- Right: Stats + Moves ---- */
            int rightL = cx + 12;
            int y = top;

            int lv  = member.getInt("lv");
            int xp  = member.getInt("xp");
            int cap = net.seep.odd.abilities.tamer.TamerLeveling.nextFor(lv);

            ctx.drawTextWithShadow(textRenderer, "Level: " + lv, rightL, y, 0xFFD080); y += 14;
            ctx.drawTextWithShadow(textRenderer, "XP: " + xp + " / " + cap, rightL, y, 0xE0E0FF); y += 6;

            // Accurate HP from NBT (server-persisted); if missing, read preview
            float curHp = member.contains("hp") ? member.getFloat("hp") : -1f;
            float maxHp = member.contains("maxh") ? member.getFloat("maxh") : -1f;
            if ((curHp < 0 || maxHp <= 0) && preview != null) {
                var aHp = preview.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH);
                if (aHp != null) { maxHp = (float)aHp.getValue(); curHp = maxHp; }
            }

            y += 8;
            ctx.drawTextWithShadow(textRenderer, "HP: " + (int)curHp + " / " + (int)maxHp, rightL, y, 0xA0FFC0);
            y += 12;
            int barW = 160, barH = 6;
            int bx = rightL, by = y;
            ctx.fill(bx, by, bx + barW, by + barH, 0x33000000);
            int fill = (int)(barW * Math.max(0f, Math.min(1f, curHp / Math.max(1f, maxHp))));
            ctx.fill(bx, by, bx + fill, by + barH, 0xFF4CE06A);
            y += 16;

            // Derived stats from preview AFTER applying TamerStats
            double atk = 0, spd = 0, def = 0;
            if (preview != null) {
                var aAtk = preview.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_DAMAGE);
                var aSpd = preview.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MOVEMENT_SPEED);
                var aDef = preview.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_ARMOR);
                if (aAtk != null) atk = aAtk.getValue();
                if (aSpd != null) spd = aSpd.getValue();
                if (aDef != null) def = aDef.getValue();
            }
            ctx.drawTextWithShadow(textRenderer,
                    String.format("ATK: %.1f   SPD: %.2f   DEF: %.1f", atk, spd, def),
                    rightL, y, 0xFFFFFF); y += 18;

            // moves (friendly names)
            ctx.drawTextWithShadow(textRenderer, "Moves:", rightL, y, 0xFFD080); y += 12;
            int mc = Math.max(0, member.getInt("mc"));
            for (int i = 0; i < mc; i++) {
                String mId = member.getString("m" + i);
                String mName;
                try { mName = net.seep.odd.abilities.tamer.TamerMoves.nameOf(mId); }
                catch (Throwable t) { mName = mId; }
                ctx.drawTextWithShadow(textRenderer, "• " + mName, rightL + 8, y, 0xFFE0B0);
                y += 11;
            }

            super.render(ctx, mouseX, mouseY, delta);
        }

        /* ---- Interaction: rotate + zoom only inside the model box ---- */
        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button == 0 && insideBox(mx, my)) { rotating = true; lastMx = (int)mx; lastMy = (int)my; return true; }
            return super.mouseClicked(mx, my, button);
        }
        @Override
        public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
            if (rotating && button == 0) {
                rotX += (float) dx; rotY += (float) dy;
                rotY = Math.max(-80f, Math.min(80f, rotY));
                lastMx = (int) mx; lastMy = (int) my;
                return true;
            }
            return super.mouseDragged(mx, my, button, dx, dy);
        }
        @Override
        public boolean mouseReleased(double mx, double my, int button) {
            if (button == 0 && rotating) { rotating = false; return true; }
            return super.mouseReleased(mx, my, button);
        }
        @Override
        public boolean mouseScrolled(double mx, double my, double amount) {
            if (insideBox(mx, my)) { zoom = Math.max(0.6f, Math.min(2.0f, zoom + (float)amount * 0.1f)); return true; }
            return super.mouseScrolled(mx, my, amount);
        }
        private boolean insideBox(double mx, double my) { return mx >= boxL && mx <= boxR && my >= boxT && my <= boxB; }
        @Override public boolean shouldCloseOnEsc() { return true; }
        @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
    }


    /* ===== Radial Wheel ===== */

    /** Public helper so networking can open the wheel. */
    public static void openWheelScreen(NbtCompound payload) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        mc.setScreen(new WheelScreen(payload));
    }

    /** Simple radial selector: click an entry to summon; Esc cancels. */
    public static final class WheelScreen extends Screen {
        private final List<NbtCompound> members = new ArrayList<>();
        private int hovered = -1;

        public WheelScreen(NbtCompound payload) {
            super(Text.literal("Choose Companion"));
            NbtList arr = payload.getList("party", NbtCompound.COMPOUND_TYPE);
            for (int i = 0; i < arr.size(); i++) members.add(arr.getCompound(i));
        }

        /** Command wheel (unchanged layout, just calls the new mode handlers). */
        public static final class CommandWheelScreen extends Screen {
            private final boolean hasActive;
            private int hovered = -1; // 0=PASSIVE,1=FOLLOW,2=RECALL,3=AGGRESSIVE
            private final int currentMode;

            public CommandWheelScreen(boolean hasActive, int modeOrdinal) {
                super(Text.literal("Command"));
                this.hasActive = hasActive;
                this.currentMode = Math.max(0, Math.min(2, modeOrdinal));
            }

            @Override public boolean shouldPause() { return false; }
            @Override public boolean shouldCloseOnEsc() { return true; }

            @Override
            public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
                renderBackground(ctx);
                ctx.fill(0, 0, width, height, 0x66000000);

                int cx = width / 2, cy = height / 2, R = 84;
                hovered = -1;
                String[] labels = {"Passive","Follow","Recall","Aggressive"};

                for (int i = 0; i < 4; i++) {
                    int color = (i == currentMode ? 0xAA22AA22 : 0xAA222222);
                    int x0 = (i==0||i==3) ? cx : 0;
                    int y0 = (i<=1) ? 0 : cy;
                    int x1 = (i==0||i==1) ? width : cx;
                    int y1 = (i<=1) ? cy : height;
                    ctx.fill(x0, y0, x1, y1, color);
                    double a = (Math.PI/2) * i + Math.PI/4;
                    int lx = cx + (int)(Math.cos(a) * (R + 22));
                    int ly = cy + (int)(Math.sin(a) * (R + 22));
                    ctx.drawTextWithShadow(textRenderer, labels[i], lx - textRenderer.getWidth(labels[i])/2, ly - 4, 0xFFFFFFFF);
                }

                double dx = mouseX - cx, dy = mouseY - cy;
                if (Math.hypot(dx, dy) > 24) {
                    double ang = Math.atan2(dy, dx); if (ang < 0) ang += Math.PI * 2;
                    hovered = (int)Math.floor(ang / (Math.PI / 2)) % 4;
                    ctx.drawTextWithShadow(textRenderer, "> " + labels[hovered], cx - 32, cy - R - 24, 0xFFFFFFAA);
                }

                super.render(ctx, mouseX, mouseY, delta);
            }

            @Override public boolean mouseClicked(double x, double y, int button) {
                if (button == 0) { choose(); return true; }
                return super.mouseClicked(x, y, button);
            }
            @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
                if (keyCode == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
                if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_E) { choose(); return true; }
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
            private void choose() {
                if (hovered == 2) {
                    if (hasActive) net.seep.odd.abilities.net.TamerNet.sendRecall();
                } else if (hovered >= 0) {
                    net.seep.odd.abilities.net.TamerNet.sendModeSet(hovered); // enum order preserved
                }
                close();
            }
            public void close() {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc != null) mc.setScreen(null);
            }
        }

        @Override public boolean shouldPause() { return false; }

        @Override
        public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
            renderBackground(ctx);
            ctx.fill(0, 0, width, height, 0x88000000);

            int cx = width / 2, cy = height / 2;
            int R  = 84;
            hovered = -1;

            int n = Math.max(1, members.size());
            for (int i = 0; i < n; i++) {
                double ang = (-Math.PI / 2) + (2 * Math.PI) * i / n;
                int ix = cx + (int)(R * Math.cos(ang));
                int iy = cy + (int)(R * Math.sin(ang));

                int dx = mouseX - ix, dy = mouseY - iy;
                boolean hot = (dx*dx + dy*dy) <= (38*38);
                if (hot) hovered = i;

                int box = hot ? 0x66FFFFFF : 0x33000000;
                ctx.fill(ix - 42, iy - 18, ix + 42, iy + 18, box);

                NbtCompound m = members.get(i);
                String nick = m.getString("nick");
                if (nick == null || nick.isEmpty()) nick = m.getString("type");
                int lv = m.getInt("lv");
                String label = nick + "  Lv." + lv;

                // icon, if present
                if (m.contains("icon", 8)) {
                    Identifier iconId = new Identifier(m.getString("icon"));
                    ctx.drawTexture(iconId, ix - 38, iy - 8, 0, 0, 16, 16, 16, 16);
                }

                int tw = textRenderer.getWidth(label);
                ctx.drawTextWithShadow(textRenderer, label, ix - tw/2 + 6, iy - 6, hot ? 0xFFFFFF : 0xD0D0D0);
            }

            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Click to summon • Esc to cancel"),
                    cx, cy + R + 26, 0xA0E0FF);

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
