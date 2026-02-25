// src/main/java/net/seep/odd/block/combiner/enchant/client/GazeOfTheEndClient.java
package net.seep.odd.block.combiner.enchant.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import net.seep.odd.block.combiner.enchant.CombinerEnchantments;
import net.seep.odd.block.combiner.enchant.GazeOfTheEndNet;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public final class GazeOfTheEndClient implements GazeOfTheEndNet.GazeClientSink {
    private static boolean inited = false;

    private static KeyBinding TOGGLE;

    private static boolean enabled = false;

    private static int lastTargetId = -1;
    private static long lastReqAt = 0;

    private static float lastHp = 0;
    private static float lastMax = 1;
    private static long lastUpdateClientTime = 0;

    // ✅ ~50 blocks more than vanilla “crosshair” feel
    private static final double GAZE_RANGE = 55.0;

    public static void init() {
        if (inited) return;
        inited = true;

        GazeOfTheEndFx.init();

        TOGGLE = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.oddities.gaze_of_the_end",
                GLFW.GLFW_KEY_G,
                "key.categories.oddities"
        ));

        // networking
        GazeOfTheEndNet.registerClient(new GazeOfTheEndClient());

        ClientTickEvents.END_CLIENT_TICK.register(GazeOfTheEndClient::tick);
        HudRenderCallback.EVENT.register((ctx, tickDelta) -> renderHud(ctx));
    }

    private static void tick(MinecraftClient mc) {
        if (mc == null) return;
        if (mc.player == null || mc.world == null) {
            setEnabled(false, null);
            return;
        }

        while (TOGGLE.wasPressed()) {
            var helmet = mc.player.getEquippedStack(EquipmentSlot.HEAD);
            int lvl = (helmet == null || helmet.isEmpty() || CombinerEnchantments.EYE == null)
                    ? 0 : EnchantmentHelper.getLevel(CombinerEnchantments.EYE, helmet);

            if (lvl <= 0) {
                setEnabled(false, mc);
                mc.player.sendMessage(Text.literal("§5You need the Eye trim helmet to use Gaze Of The End."), true);
                break;
            }

            setEnabled(!enabled, mc);
        }

        // auto-disable if helmet removed
        if (enabled) {
            var helmet = mc.player.getEquippedStack(EquipmentSlot.HEAD);
            int lvl = (helmet == null || helmet.isEmpty() || CombinerEnchantments.EYE == null)
                    ? 0 : EnchantmentHelper.getLevel(CombinerEnchantments.EYE, helmet);
            if (lvl <= 0) {
                setEnabled(false, mc);
                return;
            }
        }

        if (!enabled) {
            lastTargetId = -1;
            return;
        }

        // ✅ long-range living-entity raycast
        int targetId = findLookedAtLivingEntityId(mc, GAZE_RANGE);

        boolean changed = targetId != lastTargetId;
        if (changed) {
            lastTargetId = targetId;
            lastHp = 0;
            lastMax = 1;
            lastUpdateClientTime = 0;
        }

        if (targetId >= 0) {
            long now = mc.world.getTime();
            if (now - lastReqAt >= 3) { // every ~3 ticks
                lastReqAt = now;

                PacketByteBuf b = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
                b.writeVarInt(targetId);
                ClientPlayNetworking.send(GazeOfTheEndNet.C2S_REQUEST, b);
            }
        }
    }

    /**
     * Finds a LivingEntity the player is looking at within `range`.
     * Uses block raycast to clip the entity raycast so you can't see through walls.
     */
    private static int findLookedAtLivingEntityId(MinecraftClient mc, double range) {
        if (mc.player == null || mc.world == null) return -1;

        float tickDelta = 1.0f;

        Vec3d cam = mc.player.getCameraPosVec(tickDelta);
        Vec3d dir = mc.player.getRotationVec(tickDelta);

        // block clip
        HitResult blockHit = mc.player.raycast(range, tickDelta, false);
        double maxDist = range;
        if (blockHit != null && blockHit.getType() != HitResult.Type.MISS) {
            maxDist = cam.distanceTo(blockHit.getPos());
        }

        Vec3d end = cam.add(dir.multiply(maxDist));

        // broadened search box along view
        Box box = mc.player.getBoundingBox().stretch(dir.multiply(maxDist)).expand(1.0, 1.0, 1.0);

        EntityHitResult ehr = ProjectileUtil.raycast(
                mc.player,
                cam,
                end,
                box,
                e -> (e instanceof LivingEntity le) && le.isAlive() && e != mc.player,
                maxDist * maxDist
        );

        if (ehr != null && ehr.getEntity() instanceof LivingEntity le) {
            return le.getId();
        }
        return -1;
    }

    private static void setEnabled(boolean v, MinecraftClient mc) {
        enabled = v;
        GazeOfTheEndFx.setActive(v);

        if (!v) {
            lastTargetId = -1;
            lastHp = 0;
            lastMax = 1;
            lastUpdateClientTime = 0;
        }

        if (mc != null && mc.player != null) {
            mc.player.sendMessage(Text.literal(v ? "§5Gaze Of The End: §aON" : "§5Gaze Of The End: §cOFF"), true);
        }
    }

    @Override
    public void onHealth(int entityId, float hp, float maxHp) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null) return;

        if (!enabled) return;
        if (entityId != lastTargetId) return;

        lastHp = hp;
        lastMax = Math.max(1.0f, maxHp);
        lastUpdateClientTime = mc.world.getTime();
    }

    private static void renderHud(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null) return;
        if (!enabled) return;

        long now = mc.world.getTime();
        if (lastTargetId < 0) return;
        if (now - lastUpdateClientTime > 10) return;

        var e = mc.world.getEntityById(lastTargetId);
        if (!(e instanceof LivingEntity le) || !le.isAlive()) return;

        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();

        int cx = sw / 2;
        int cy = sh / 2;

        float ratio = Math.max(0f, Math.min(1f, lastHp / lastMax));

        int barW = 110;
        int barH = 8;

        int x = cx - barW / 2;
        int y = cy + 20;

        int bg = 0xAA1A0B24;
        int fg = 0xCCB04BFF;
        int edge = 0xFF6D2FFF;

        ctx.fill(x - 1, y - 1, x + barW + 1, y + barH + 1, edge);
        ctx.fill(x, y, x + barW, y + barH, bg);

        int filled = (int)(barW * ratio);
        if (filled > 0) ctx.fill(x, y, x + filled, y + barH, fg);

        String name = le.getDisplayName().getString();
        String hpTxt = String.format("❤ %.1f / %.1f", lastHp, lastMax);

        ctx.drawText(mc.textRenderer, name, x, y - 10, 0xFFD6B3FF, true);
        ctx.drawText(mc.textRenderer, hpTxt,
                x + barW - mc.textRenderer.getWidth(hpTxt),
                y - 10,
                0xFFE7D4FF,
                true);
    }
}