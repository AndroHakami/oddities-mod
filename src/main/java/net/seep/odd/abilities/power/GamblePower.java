package net.seep.odd.abilities.power;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import net.seep.odd.Oddities;
import net.seep.odd.abilities.gamble.GambleMode;
import net.seep.odd.abilities.gamble.item.GambleRevolverItem;

import java.util.Map;
import java.util.UUID;

public final class GamblePower implements Power {
    @Override public String id() { return "gamble"; }
    @Override public boolean hasSlot(String slot) { return "primary".equals(slot) || "secondary".equals(slot); }
    @Override public long cooldownTicks() { return 5; }
    @Override public long secondaryCooldownTicks() { return 20; }
    @Override public long thirdCooldownTicks() { return 0; }

    @Override
    public Identifier iconTexture(String slot) {
        return switch (slot) {
            case "primary"   -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/gamble_toggle.png");
            case "secondary" -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/gamble_reload.png");
            default          -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/ability_default.png");
        };
    }
    @Override public String slotTitle(String slot) {
        return switch (slot) {
            case "primary" -> "SWITCH UP!";
            case "secondary" -> "HEARTS OUT!";
            default -> Power.super.slotTitle(slot);
        };
    }

    @Override public String longDescription() { return "Revolver fueled by hearts. Toggle bullet mode and reload with the secondary slot."; }
    @Override public String slotLongDescription(String slot) {
        return switch (slot) {
            case "primary"   -> "Toggle bullet mode: Debuff / Buff / Shoot";
            case "secondary" -> "Reload the Gamble revolver (consumes hearts).";
            default          -> "";
        };
    }
    @Override public Identifier portraitTexture() { return new Identifier(Oddities.MOD_ID, "textures/gui/overview/gamble.png"); }

    /* ===== per-player state ===== */
    private static final class St { GambleMode mode = GambleMode.SHOOT; }
    private static final Map<UUID, St> DATA = new Object2ObjectOpenHashMap<>();
    private static St S(PlayerEntity p) { return DATA.computeIfAbsent(p.getUuid(), u -> new St()); }

    public static GambleMode getMode(PlayerEntity p) { return S(p).mode; }
    public static void setMode(PlayerEntity p, GambleMode m) { S(p).mode = m; }

    @Override
    public void activate(ServerPlayerEntity p) {
        St st = S(p);
        st.mode = st.mode.next();
        p.sendMessage(Text.literal("Gamble mode: " + st.mode.display()), true);
    }

    @Override
    public void activateSecondary(ServerPlayerEntity p) {
        // find the revolver in either hand and ask it to reload (no vanilla “use” animations)
        for (Hand h : Hand.values()) {
            ItemStack s = p.getStackInHand(h);
            if (s.getItem() instanceof GambleRevolverItem gi) {
                gi.requestReload(p, s, h);
                return;
            }
        }
        p.sendMessage(Text.literal("No Gamble revolver equipped."), true);
    }

    @Environment(EnvType.CLIENT)
    public static final class Hud {
        public static void init() {
            HudRenderCallback.EVENT.register((DrawContext ctx, float td) -> {
                var mc = MinecraftClient.getInstance();
                if (mc.player == null) return;
                String label = switch (GamblePower.getMode(mc.player)) {
                    case BUFF -> "Buff";
                    case DEBUFF -> "Debuff";
                    default -> "Shoot";
                };
                int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
                int w = mc.textRenderer.getWidth(label);
                ctx.drawText(mc.textRenderer, label, sw/2 - w/2, sh - 55, 0xFFEAA73C, true);
            });
            WorldRenderEvents.START.register((WorldRenderContext ctx) -> {});
        }
    }
}
