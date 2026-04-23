package net.seep.odd.abilities.darkknight.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.seep.odd.abilities.darkknight.DarkKnightRuntime;
import net.seep.odd.abilities.power.DarkKnightPower;
import net.seep.odd.entity.darkknight.DarkShieldEntity;

import java.util.Locale;
import java.util.UUID;

@Environment(EnvType.CLIENT)
public final class DarkKnightShieldHudClient {
    private DarkKnightShieldHudClient() {}

    private static boolean inited = false;
    private static float cachedHealth = DarkKnightRuntime.MAX_SHIELD_HEALTH;
    private static long recalledAtTick = 0L;
    private static boolean hadActiveShieldLastFrame = false;

    public static void init() {
        if (inited) {
            return;
        }
        inited = true;

        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> render(drawContext));
    }

    private static void render(DrawContext drawContext) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.options.hudHidden) {
            return;
        }

        if (!DarkKnightPower.hasPower(client.player)) {
            return;
        }

        ShieldHudState state = resolveState(client);

        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();

        int barWidth = 92;
        int barHeight = 8;
        int x = (screenWidth / 2) - (barWidth / 2);
        int y = screenHeight - 63;

        drawContext.fill(x - 1, y - 1, x + barWidth + 1, y + barHeight + 1, 0xAA12091A);
        drawContext.fill(x, y, x + barWidth, y + barHeight, 0x66000000);

        float clampedHealth = MathHelper.clamp(state.health, 0.0F, DarkKnightRuntime.MAX_SHIELD_HEALTH);
        int filled = MathHelper.ceil((clampedHealth / DarkKnightRuntime.MAX_SHIELD_HEALTH) * barWidth);
        if (filled > 0) {
            drawContext.fill(x, y, x + filled, y + barHeight, 0xCC5A238D);
        }

        String title = "DARK SHIELD";
        String hp = String.format(
                Locale.ROOT,
                "%.1f / %.1f",
                clampedHealth / 2.0F,
                DarkKnightRuntime.MAX_SHIELD_HEALTH / 2.0F
        );

        String extra;
        if (state.active) {
            extra = "ACTIVE";
        } else if (clampedHealth >= DarkKnightRuntime.MAX_SHIELD_HEALTH - 0.001F) {
            extra = "READY";
        } else {
            extra = "CHARGING";
        }

        drawContext.drawTextWithShadow(client.textRenderer, title, x, y - 11, 0xD8B0FF);
        drawContext.drawTextWithShadow(client.textRenderer, hp, x, y + 10, 0xFFFFFF);
        drawContext.drawTextWithShadow(
                client.textRenderer,
                extra,
                x + barWidth - client.textRenderer.getWidth(extra),
                y + 10,
                0xD8B0FF
        );
    }

    private static ShieldHudState resolveState(MinecraftClient client) {
        UUID ownerUuid = client.player.getUuid();
        DarkShieldEntity activeShield = null;

        for (Entity entity : client.world.getEntities()) {
            if (entity instanceof DarkShieldEntity shield && ownerUuid.equals(shield.getOwnerUuid()) && shield.isAlive()) {
                activeShield = shield;
                break;
            }
        }

        long now = client.world.getTime();

        if (activeShield != null) {
            cachedHealth = MathHelper.clamp(
                    activeShield.getShieldHealth(),
                    0.0F,
                    DarkKnightRuntime.MAX_SHIELD_HEALTH
            );
            hadActiveShieldLastFrame = true;
            return new ShieldHudState(true, cachedHealth);
        }

        if (hadActiveShieldLastFrame) {
            recalledAtTick = now;
            hadActiveShieldLastFrame = false;
        }

        float rechargedHealth = Math.min(
                DarkKnightRuntime.MAX_SHIELD_HEALTH,
                cachedHealth + Math.max(0L, now - recalledAtTick) * DarkKnightRuntime.REGEN_PER_TICK
        );

        cachedHealth = rechargedHealth;
        return new ShieldHudState(false, rechargedHealth);
    }

    private record ShieldHudState(boolean active, float health) {}
}