// FILE: src/main/java/net/seep/odd/abilities/gamble/item/client/GambleRevolverClient.java
package net.seep.odd.abilities.gamble.item.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

import net.seep.odd.Oddities;
import net.seep.odd.abilities.gamble.GambleMode;
import net.seep.odd.abilities.gamble.item.GambleRevolverItem;
import net.seep.odd.abilities.power.GamblePower;

@Environment(EnvType.CLIENT)
public final class GambleRevolverClient {
    private GambleRevolverClient() {}

    private static boolean s2cRegistered = false;
    private static boolean tickRegistered = false;

    private static boolean lastAttackDown = false;
    private static int clientCooldown = 0;

    // ✅ NEW: edge-detect synced state (so anims work even if prediction / render context is weird)
    private static boolean lastMainCooldown = false;
    private static boolean lastOffCooldown  = false;
    private static boolean lastMainReload   = false;
    private static boolean lastOffReload    = false;

    public static void init() {
        if (!s2cRegistered) {
            s2cRegistered = true;

            // S2C: reload anim trigger (kept)
            ClientPlayNetworking.registerGlobalReceiver(GambleRevolverItem.PKT_RELOAD_ANIM, (client, handler, buf, response) -> {
                final int handOrd = buf.readVarInt();
                client.execute(() -> GambleRevolverItem.clientStartReloadAnim(handOrd));
            });
        }

        Hud.initOnce();

        if (tickRegistered) return;
        tickRegistered = true;

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            // tick down anim counters + client throttle
            GambleRevolverItem.clientTickDownAnimCounters();
            if (clientCooldown > 0) clientCooldown--;

            ItemStack main = client.player.getMainHandStack();
            ItemStack off  = client.player.getOffHandStack();

            boolean mainIs = main.getItem() instanceof GambleRevolverItem;
            boolean offIs  = off.getItem()  instanceof GambleRevolverItem;
            boolean holding = mainIs || offIs;

            boolean nowDown = client.options.attackKey.isPressed();

            if (!holding) {
                lastAttackDown = false;
                lastMainCooldown = false;
                lastOffCooldown  = false;
                lastMainReload   = false;
                lastOffReload    = false;
                return;
            }

            // ✅ FIRE ANIM (synced): cooldown flips OFF -> ON
            if (mainIs) {
                boolean cd = client.player.getItemCooldownManager().isCoolingDown(main.getItem());
                if (cd && !lastMainCooldown) {
                    GambleRevolverItem.clientStartFireAnim(Hand.MAIN_HAND);
                }
                lastMainCooldown = cd;
            } else lastMainCooldown = false;

            if (offIs) {
                boolean cd = client.player.getItemCooldownManager().isCoolingDown(off.getItem());
                if (cd && !lastOffCooldown) {
                    GambleRevolverItem.clientStartFireAnim(Hand.OFF_HAND);
                }
                lastOffCooldown = cd;
            } else lastOffCooldown = false;

            // ✅ RELOAD ANIM (synced): NBT gamble_reloading flips false -> true
            if (mainIs) {
                boolean r = main.hasNbt() && main.getNbt().getBoolean("gamble_reloading");
                if (r && !lastMainReload) {
                    GambleRevolverItem.clientStartReloadAnim(Hand.MAIN_HAND.ordinal());
                }
                lastMainReload = r;
            } else lastMainReload = false;

            if (offIs) {
                boolean r = off.hasNbt() && off.getNbt().getBoolean("gamble_reloading");
                if (r && !lastOffReload) {
                    GambleRevolverItem.clientStartReloadAnim(Hand.OFF_HAND.ordinal());
                }
                lastOffReload = r;
            } else lastOffReload = false;

            // POWERLESS: don't send packets (but synced anim edges can still play)
            if (GamblePower.isPowerless(client.player)) {
                lastAttackDown = nowDown;
                return;
            }

            // LMB edge fire
            boolean edge = nowDown && !lastAttackDown;
            lastAttackDown = nowDown;

            if (edge && clientCooldown <= 0) {
                Hand hand = mainIs ? Hand.MAIN_HAND : Hand.OFF_HAND;
                ItemStack held = client.player.getStackInHand(hand);

                // respect synced vanilla cooldown too
                if (client.player.getItemCooldownManager().isCoolingDown(held.getItem())) return;

                clientCooldown = GambleRevolverItem.FIRE_COOLDOWN_T;

                // (keep local prediction too; harmless)
                GambleRevolverItem.clientStartFireAnim(hand);

                // send fire to server
                PacketByteBuf out = PacketByteBufs.create();
                out.writeVarInt(hand.ordinal());
                ClientPlayNetworking.send(GambleRevolverItem.PKT_FIRE, out);

                if (client.interactionManager != null) client.interactionManager.cancelBlockBreaking();
            }
        });
    }

    /* =========================  REVOLVER HUD  ========================= */
    private static final class Hud {
        private static boolean registered = false;

        private static final Identifier HUD_BG_TEX   = new Identifier(Oddities.MOD_ID, "textures/gui/hud/gamble_hud_bg.png");
        private static final Identifier HUD_ICON_TEX = new Identifier(Oddities.MOD_ID, "textures/gui/hud/gamble_revolver_icon.png");

        private static final int BG_TEX_W   = 160, BG_TEX_H   = 64;
        private static final int ICON_TEX_W = 32,  ICON_TEX_H = 32;

        private static final int BG_W = 160, BG_H = 64;
        private static final int ICON_W = 28, ICON_H = 28;

        private static final int PAD_LEFT   = 8;
        private static final int PAD_RIGHT  = 8;
        private static final int PAD_TOP    = 8;
        private static final int PAD_BOTTOM = 8;
        private static final int GAP_ICON_TEXT = 8;
        private static final int GAP_TEXT_V    = 4;

        private static final float SCALE_MODE_DESIRED = 1.25f;
        private static final float SCALE_AMMO_DESIRED = 1.45f;
        private static final float SCALE_MIN          = 0.60f;

        static void initOnce() {
            if (registered) return;
            registered = true;

            HudRenderCallback.EVENT.register((DrawContext ctx, float tickDelta) -> {
                final var mc = MinecraftClient.getInstance();
                if (mc == null || mc.player == null) return;

                ItemStack main = mc.player.getMainHandStack();
                ItemStack off  = mc.player.getOffHandStack();
                boolean holding = main.getItem() instanceof GambleRevolverItem || off.getItem() instanceof GambleRevolverItem;
                if (!holding) return;

                int sh = mc.getWindow().getScaledHeight();

                int x = 8;
                int y = sh - BG_H - 8;

                var m = ctx.getMatrices();
                m.push();
                m.translate(0, 0, 1000);

                ItemStack stack = (main.getItem() instanceof GambleRevolverItem) ? main : off;
                int ammo = GambleRevolverItem.getAmmo(stack);
                int max  = GambleRevolverItem.MAG_SIZE;

                ctx.drawTexture(HUD_BG_TEX, x, y, 0, 0, BG_W, BG_H, BG_TEX_W, BG_TEX_H);

                int iconX = x + PAD_LEFT;
                int iconY = y + (BG_H - ICON_H) / 2;
                ctx.drawTexture(HUD_ICON_TEX, iconX, iconY, 0, 0, ICON_W, ICON_H, ICON_TEX_W, ICON_TEX_H);

                int textLeft = iconX + ICON_W + GAP_ICON_TEXT;
                int textRight = x + BG_W - PAD_RIGHT;
                int usableW = Math.max(0, textRight - textLeft);
                int usableH = BG_H - PAD_TOP - PAD_BOTTOM;

                GambleMode mode = GamblePower.getMode(mc.player);
                String modeStr = switch (mode) {
                    case BUFF   -> "Buff";
                    case DEBUFF -> "Debuff";
                    default     -> "Shoot";
                };
                String ammoStr = ammo + "/" + max;

                int modeColor = switch (mode) {
                    case BUFF   -> 0xFF7EE08F;
                    case DEBUFF -> 0xFFC77CFF;
                    default     -> 0xFFFFC84A;
                };
                int ammoColor = (ammo > 0) ? 0xFFFFFFFF : 0xFFFF6868;

                float modeScale = fitWidthScale(mc, modeStr, SCALE_MODE_DESIRED, usableW);
                float ammoScale = fitWidthScale(mc, ammoStr, SCALE_AMMO_DESIRED, usableW);

                int fh = mc.textRenderer.fontHeight;
                float totalH = fh * modeScale + GAP_TEXT_V + fh * ammoScale;
                if (totalH > usableH) {
                    float shrink = Math.max(SCALE_MIN, usableH / totalH);
                    modeScale *= shrink;
                    ammoScale *= shrink;
                    totalH = fh * modeScale + GAP_TEXT_V + fh * ammoScale;
                }

                int textTop = y + (BG_H - (int)totalH) / 2;

                drawScaledText(ctx, mc, modeStr, textLeft, textTop, modeColor, modeScale, 1010);

                int modePixH = Math.round(fh * modeScale);
                drawScaledText(ctx, mc, ammoStr, textLeft, textTop + modePixH + GAP_TEXT_V, ammoColor, ammoScale, 1005);

                m.pop();
            });
        }

        private static float fitWidthScale(MinecraftClient mc, String text, float desired, int maxPx) {
            int w = mc.textRenderer.getWidth(text);
            if (w <= 0) return desired;
            float maxScale = maxPx / (float) w;
            return Math.max(SCALE_MIN, Math.min(desired, maxScale));
        }

        private static void drawScaledText(DrawContext ctx, MinecraftClient mc,
                                           String text, int px, int py, int color, float scale, float extraZ) {
            var m = ctx.getMatrices();
            m.push();
            m.translate(px, py, extraZ);
            m.scale(scale, scale, 1f);
            ctx.drawText(mc.textRenderer, text, 0, 0, color, true);
            m.pop();
        }
    }
}