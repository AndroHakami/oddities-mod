package net.seep.odd.abilities.buddymorph.client;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.seep.odd.abilities.buddymorph.BuddymorphNet;
import org.joml.Quaternionf;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Buddymorph picker with 3D previews (anti-jitter) and optional charge badges.
 * - Works with old payload (IDs only) via SimpleBuddymorphScreen(List<String> ids)
 * - Works with new payload (IDs + charges) via SimpleBuddymorphScreen(List<String> ids, List<Integer> charges)
 * - "You" tile uses a frozen snapshot of your player (doesn’t change when you morph)
 * - Drag = rotate; Scroll = zoom; Click = select
 */
@Environment(EnvType.CLIENT)
public final class SimpleBuddymorphScreen extends Screen {
    private final List<Entry> entries = new ArrayList<>();
    private final List<Rect>  rects   = new ArrayList<>();

    private static final int COLS   = 4;
    private static final int CELL_W = 88;
    private static final int CELL_H = 88;
    private static final int PAD    = 10;

    // drag/zoom state for interactive previews
    private int draggingIndex = -1;
    private double lastMx, lastMy;

    // stable anchor captured on open (we don't follow camera bob)
    private static double ANCHOR_X, ANCHOR_Y, ANCHOR_Z;

    // player snapshot used for the "You" tile, independent of morphs
    private static OtherClientPlayerEntity SELF_SNAPSHOT;

    /* ----------------- Constructors (IDs only OR IDs+charges) ----------------- */

    /** Back-compat: IDs only (no badges). */
    public SimpleBuddymorphScreen(List<String> ids) {
        super(Text.literal("Buddymorph"));
        setEntries(ids, null);
    }

    /** New: IDs + charges. Both lists must be same size; null/size mismatch → badges hidden. */
    public SimpleBuddymorphScreen(List<String> ids, List<Integer> charges) {
        super(Text.literal("Buddymorph"));
        setEntries(ids, charges);
    }

    /* Live updates from net handlers */
    public void updateIds(List<String> ids) {
        setEntries(ids, null);
        layoutGrid();
    }
    public void updateIds(List<String> ids, List<Integer> charges) {
        setEntries(ids, charges);
        layoutGrid();
    }

    private void setEntries(List<String> ids, List<Integer> charges) {
        entries.clear();
        entries.add(Entry.self());

        boolean useCharges = charges != null && charges.size() == ids.size();

        for (int idx = 0; idx < ids.size(); idx++) {
            try {
                Identifier id = new Identifier(ids.get(idx));
                int ch = useCharges ? Math.max(-1, charges.get(idx)) : -1; // -1 = hidden badge
                Entry e = Entry.of(id, ch);
                entries.add(e);
                // eagerly create preview so new tiles show immediately
                LivingEntity le = ensurePreview(e);
                if (le != null) freezeForPreview(le);
            } catch (Throwable ignored) {}
        }
    }

    @Override
    protected void init() {
        // take a stable anchor snapshot (camera may bob/wobble; we don't follow it)
        var mc = MinecraftClient.getInstance();
        Entity cam = mc != null ? (mc.getCameraEntity() != null ? mc.getCameraEntity() : mc.player) : null;
        if (cam != null) {
            ANCHOR_X = cam.getX();
            ANCHOR_Y = cam.getY();
            ANCHOR_Z = cam.getZ();
        }
        // create/refresh the "You" snapshot once per open
        SELF_SNAPSHOT = makeSelfSnapshot();
        layoutGrid();
    }

    private void layoutGrid() {
        rects.clear();
        int total = entries.size();
        int rows  = (int) Math.ceil(total / (double) COLS);

        int gridW = COLS * CELL_W + (COLS - 1) * PAD;
        int gridH = rows * CELL_H + (rows - 1) * PAD;
        int sx = (this.width  - gridW) / 2;
        int sy = (this.height - gridH) / 2;

        int idx = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < COLS && idx < total; c++, idx++) {
                rects.add(new Rect(sx + c * (CELL_W + PAD), sy + r * (CELL_H + PAD), CELL_W, CELL_H));
            }
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx);
        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 12, 0xFFFFFF);

        final MinecraftClient mc = MinecraftClient.getInstance();
        final boolean hasWorld = mc != null && mc.world != null;

        for (int i = 0; i < rects.size(); i++) {
            Rect r = rects.get(i);
            Entry e = entries.get(i);
            boolean hot = r.contains(mouseX, mouseY);

            // backdrop
            ctx.fill(r.x, r.y, r.x + r.w, r.y + r.h, hot ? 0xAA222222 : 0x66000000);

            // label
            String label = e.self ? "You" : friendlyName(e.typeId);
            if (label.length() > 14) label = label.substring(0, 14) + "…";
            int cx = r.x + r.w / 2;
            ctx.drawCenteredTextWithShadow(textRenderer, label, cx, r.y + 6, e.self ? 0xFFEAA73C : 0xFFEEEEEE);

            // charge badge (show only if charges >= 0 and not self)
            if (!e.self && e.charges >= 0) {
                String badge = "×" + e.charges;
                int bw = textRenderer.getWidth(badge) + 6;
                int bx = r.x + r.w - bw - 5;
                int by = r.y + 5;
                ctx.fill(bx, by, bx + bw, by + 10, 0xAA000000); // dark pill
                // green if >1, red if 0/1
                int color = (e.charges > 1) ? 0xFFB5F27A : 0xFFFF8080;
                ctx.drawTextWithShadow(textRenderer, badge, bx + 3, by + 1, color);
            }

            // 3D preview
            if (hasWorld) {
                LivingEntity toRender = e.self ? SELF_SNAPSHOT : ensurePreview(e);
                if (toRender != null) {
                    if (draggingIndex != i) e.yaw += delta * 0.6f; // idle spin only

                    int size  = scaledSize(toRender, e.zoom);
                    int drawY = r.y + r.h - 12;

                    drawEntityInGui(ctx, cx, drawY, size, e.yaw, e.pitch, toRender, delta);
                }
            }

            if (hot) ctx.drawBorder(r.x, r.y, r.w, r.h, 0xFFB089F9);
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    /* -------- interaction -------- */

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (int i = 0; i < rects.size(); i++) {
                if (rects.get(i).contains(mx, my)) {
                    draggingIndex = i;
                    lastMx = mx; lastMy = my;
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0 && draggingIndex != -1) {
            int i = draggingIndex;
            draggingIndex = -1;

            if (rects.get(i).contains(mx, my) && Math.hypot(mx - lastMx, my - lastMy) < 4.0) {
                Entry e = entries.get(i);
                var out = PacketByteBufs.create();
                out.writeBoolean(e.self);
                if (!e.self) out.writeString(e.typeId.toString());
                ClientPlayNetworking.send(BuddymorphNet.C2S_PICK, out);
                MinecraftClient.getInstance().setScreen(null);
                return true;
            }
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button == 0 && draggingIndex >= 0 && draggingIndex < entries.size()) {
            Entry e = entries.get(draggingIndex);
            if (rects.get(draggingIndex).contains(mx, my)) {
                e.yaw   += (float) dx * 0.6f;
                e.pitch += (float) dy * 0.4f;
                e.pitch = clamp(e.pitch, -35f, 35f);
            }
            lastMx = mx; lastMy = my;
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        for (int i = 0; i < rects.size(); i++) {
            if (rects.get(i).contains(mx, my)) {
                Entry e = entries.get(i);
                e.zoom = clamp(e.zoom + (float)amount * 0.1f, 0.6f, 2.0f);
                return true;
            }
        }
        return super.mouseScrolled(mx, my, amount);
    }

    @Override public boolean shouldCloseOnEsc() { return true; }

    /* -------- helpers -------- */

    private static OtherClientPlayerEntity makeSelfSnapshot() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || mc.player == null) return null;
        ClientWorld cw = (ClientWorld) mc.world;
        GameProfile profile = mc.player.getGameProfile();

        OtherClientPlayerEntity snap = new OtherClientPlayerEntity(cw, profile);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            snap.equipStack(slot, mc.player.getEquippedStack(slot).copy());
        }
        snap.refreshPositionAndAngles(ANCHOR_X, ANCHOR_Y, ANCHOR_Z, 0f, 0f);
        freezeForPreview(snap);
        return snap;
    }

    private static LivingEntity ensurePreview(Entry e) {
        if (e.self) return SELF_SNAPSHOT; // frozen player
        if (e.preview != null) return e.preview;

        var mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null) return null;

        var type = Registries.ENTITY_TYPE.get(e.typeId);
        if (type != null) {
            Entity raw = type.create(mc.world);
            if (raw instanceof LivingEntity le) {
                le.setCustomNameVisible(false);
                le.setSilent(true);
                le.setNoGravity(true);
                le.noClip = true;
                le.refreshPositionAndAngles(ANCHOR_X, ANCHOR_Y, ANCHOR_Z, 0f, 0f);
                freezeForPreview(le);
                e.preview = le;
            }
        }
        return e.preview;
    }

    /** Make a preview entity inert/stable (no AI, no motion). */
    private static void freezeForPreview(LivingEntity le) {
        try {
            // Best-effort AI off (name varies across versions; ignore if absent)
            Method m = LivingEntity.class.getMethod("setAiDisabled", boolean.class);
            m.invoke(le, true);
        } catch (Throwable ignored) {}
        le.setVelocity(0, 0, 0);
        le.velocityDirty = true;
        le.hurtTime = 0;
        le.deathTime = 0;
        le.handSwingProgress = 0f;
        le.setOnGround(false);
        // keep previous angles same as current so interpolation doesn't wiggle between ticks
        le.prevPitch = le.getPitch();
        le.prevYaw = le.getYaw();
        le.prevBodyYaw = le.bodyYaw;
        le.prevHeadYaw = le.getHeadYaw();
        // also freeze prev position == position (some renderers sample it)
        try {
            Field px = Entity.class.getDeclaredField("prevX");
            Field py = Entity.class.getDeclaredField("prevY");
            Field pz = Entity.class.getDeclaredField("prevZ");
            px.setAccessible(true); py.setAccessible(true); pz.setAccessible(true);
            px.setDouble(le, le.getX());
            py.setDouble(le, le.getY());
            pz.setDouble(le, le.getZ());
        } catch (Throwable ignored) {}
    }

    private static int scaledSize(LivingEntity le, float zoom) {
        float h = Math.max(0.6f, le.getHeight());
        int base = Math.round(38f / h);
        return Math.max(18, Math.min(54, Math.round(base * zoom)));
    }

    private static float clamp(float v, float a, float b) { return v < a ? a : (v > b ? b : v); }

    private static String friendlyName(Identifier id) {
        var t = Registries.ENTITY_TYPE.get(id);
        return (t != null) ? t.getName().getString() : id.getPath().replace('_', ' ');
    }

    /** Vanilla-like GUI entity rendering with explicit yaw/pitch, but with interpolation fully disabled. */
    private static void drawEntityInGui(DrawContext ctx, int x, int y, int size, float yawDeg, float pitchDeg, LivingEntity entity, float delta) {
        float f = yawDeg / 40.0f;     // vanilla mouseX/40
        float g = -pitchDeg / 20.0f;  // vanilla -mouseY/40 * 20

        MatrixStack modelView = RenderSystem.getModelViewStack();
        modelView.push();
        modelView.translate(x, y, 1050.0f);
        modelView.scale(1.0f, 1.0f, -1.0f);
        RenderSystem.applyModelViewMatrix();

        MatrixStack matrices = new MatrixStack();
        matrices.translate(0.0, 0.0, 1000.0);
        matrices.scale((float)size, (float)size, (float)size);

        Quaternionf z180 = RotationAxis.POSITIVE_Z.rotationDegrees(180.0f);
        Quaternionf xRot = RotationAxis.POSITIVE_X.rotationDegrees(g * 20.0f);
        z180.mul(xRot);
        matrices.multiply(z180);

        // Compute our target preview pose (same math vanilla uses from mouse)
        float targetBodyYaw = 180.0f + f * 20.0f;
        float targetYaw     = 180.0f + f * 40.0f;
        float targetPitch   = -g * 20.0f;
        float targetHeadYaw = targetYaw;

        // Save current state
        float saveBodyYaw = entity.bodyYaw;
        float saveYaw     = entity.getYaw();
        float savePitch   = entity.getPitch();
        float saveHeadYaw = entity.getHeadYaw();
        float savePrevBodyYaw = entity.prevBodyYaw;
        float savePrevYaw     = entity.prevYaw;
        float savePrevPitch   = entity.prevPitch;
        float savePrevHeadYaw = entity.prevHeadYaw;
        int   saveAge         = entity.age;

        // Hard-freeze interpolation & idle animation **this frame**
        entity.bodyYaw = targetBodyYaw;
        entity.setYaw(targetYaw);
        entity.setPitch(targetPitch);
        entity.setHeadYaw(targetHeadYaw);
        entity.prevBodyYaw = targetBodyYaw;
        entity.prevYaw     = targetYaw;
        entity.prevPitch   = targetPitch;
        entity.prevHeadYaw = targetHeadYaw;
        entity.age = 0;                       // many renders use age+delta; keep fixed
        zeroLimbAnimator(entity);             // stop limb swing on all mappings we can

        EntityRenderDispatcher dispatcher = MinecraftClient.getInstance().getEntityRenderDispatcher();
        Quaternionf camAdj = new Quaternionf(xRot).conjugate();
        dispatcher.setRotation(camAdj);
        dispatcher.setRenderShadows(false);

        VertexConsumerProvider.Immediate immediate = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
        int light = LightmapTextureManager.pack(15, 15); // fullbright

        RenderSystem.runAsFancy(() ->
                dispatcher.render(entity, 0.0, 0.0, 0.0, 0.0f, 0.0f, matrices, immediate, light) // tickDelta 0 because we disabled interpolation
        );
        immediate.draw();
        dispatcher.setRenderShadows(true);

        // Restore state
        entity.bodyYaw = saveBodyYaw;
        entity.setYaw(saveYaw);
        entity.setPitch(savePitch);
        entity.setHeadYaw(saveHeadYaw);
        entity.prevBodyYaw = savePrevBodyYaw;
        entity.prevYaw     = savePrevYaw;
        entity.prevPitch   = savePrevPitch;
        entity.prevHeadYaw = savePrevHeadYaw;
        entity.age         = saveAge;

        modelView.pop();
        RenderSystem.applyModelViewMatrix();
    }

    /** Best-effort: zero out limb animator regardless of Yarn name/version. Safe no-op if not found. */
    private static void zeroLimbAnimator(LivingEntity e) {
        try {
            // 1.20+ : e.limbAnimator.setSpeed(0f)
            Field f = LivingEntity.class.getDeclaredField("limbAnimator");
            f.setAccessible(true);
            Object la = f.get(e);
            if (la != null) {
                try {
                    Method setSpeed = la.getClass().getMethod("setSpeed", float.class);
                    setSpeed.invoke(la, 0f);
                } catch (NoSuchMethodException ignored) { }
                try {
                    Method update = la.getClass().getMethod("update", float.class, float.class);
                    update.invoke(la, 0f, 0f); // ensure phase reset
                } catch (NoSuchMethodException ignored) { }
            }
        } catch (Throwable ignored) {
            // 1.19-: fallback fields (limbAngle/limbDistance) — ignore if absent
            try {
                Field limbAngle = LivingEntity.class.getDeclaredField("limbAngle");
                Field limbDist  = LivingEntity.class.getDeclaredField("limbDistance");
                limbAngle.setAccessible(true);
                limbDist.setAccessible(true);
                limbAngle.setFloat(e, 0f);
                limbDist.setFloat(e, 0f);
            } catch (Throwable ignored2) {}
        }
    }

    /* -------- inner types -------- */

    private static final class Entry {
        final boolean self;
        final Identifier typeId;
        final int charges; // -1 = hidden badge (IDs-only mode)
        LivingEntity preview;
        float yaw = 0f, pitch = 0f;
        float zoom = 1.0f;

        static Entry self()                         { return new Entry(true,  new Identifier("minecraft", "player"), -1); }
        static Entry of(Identifier typeId, int ch)  { return new Entry(false, typeId, ch); }
        private Entry(boolean self, Identifier typeId, int charges) { this.self = self; this.typeId = typeId; this.charges = charges; }
    }

    private static final class Rect {
        final int x, y, w, h;
        Rect(int x, int y, int w, int h) { this.x = x; this.y = y; this.w = w; this.h = h; }
        boolean contains(double mx, double my) { return mx >= x && mx <= x + w && my >= y && my <= y + h; }
    }
}
