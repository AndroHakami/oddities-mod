package net.seep.odd.abilities.owl.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public final class OwlDangerSenseOverlay {
    private OwlDangerSenseOverlay() {}

    private static final Identifier VIGNETTE = new Identifier("minecraft", "textures/misc/vignette.png");
    private static float strength = 0f;

    public static void registerClient() {
        HudRenderCallback.EVENT.register(OwlDangerSenseOverlay::render);
    }

    public static void setStrength(float s) {
        strength = MathHelper.clamp(s, 0f, 1f);
    }

    public static float computeThreatStrength(MinecraftClient client) {
        if (client.player == null || client.world == null) return 0f;

        PlayerEntity me = client.player;

        Vec3d myEye = me.getCameraPosVec(1.0f);
        Vec3d myLook = me.getRotationVec(1.0f).normalize();

        float best = 0f;

        for (Entity e : client.world.getEntities()) {
            if (!(e instanceof LivingEntity le)) continue;
            if (e == me) continue;
            if (!e.isAlive()) continue;

            double dist = me.distanceTo(e);
            if (dist > 48) continue;

            Vec3d theirEye = le.getCameraPosVec(1.0f);

            // Are THEY looking at me?
            Vec3d toMe = myEye.subtract(theirEye).normalize();
            Vec3d theirLook = le.getRotationVec(1.0f).normalize();
            double lookingDot = theirLook.dotProduct(toMe);
            boolean theyLookingAtMe = lookingDot > 0.965; // ~15 degrees

            // Am I looking at THEM?
            Vec3d toThem = theirEye.subtract(myEye).normalize();
            double iSeeDot = myLook.dotProduct(toThem);
            boolean iLookingAtThem = iSeeDot > 0.70;

            boolean playerThreat = (e instanceof PlayerEntity) && theyLookingAtMe && !iLookingAtThem;

            boolean mobThreat = false;
            if (e instanceof HostileEntity && e instanceof MobEntity mob) {
                if (mob.getTarget() == me && !iLookingAtThem) mobThreat = true;
            }

            if (!playerThreat && !mobThreat) continue;

            float closeness = (float)MathHelper.clamp(1.0 - (dist / 48.0), 0.0, 1.0);
            float behindBonus = (float)MathHelper.clamp((0.70 - iSeeDot) / 0.70, 0f, 1f);
            float s = closeness * (0.55f + 0.45f * behindBonus);

            if (s > best) best = s;
        }

        return best;
    }

    private static void render(DrawContext ctx, float tickDelta) {
        if (strength <= 0.01f) return;

        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();

        // vignette dark
        ctx.drawTexture(VIGNETTE, 0, 0, -90, 0, 0, w, h, w, h);

        // purple hue overlay
        float a = MathHelper.clamp(strength * 0.85f, 0f, 0.85f);
        int purple = ((int)(a * 160) << 24) | (0x9A << 16) | (0x4D << 8) | 0xFF;
        ctx.fill(0, 0, w, h, purple);
    }
}
