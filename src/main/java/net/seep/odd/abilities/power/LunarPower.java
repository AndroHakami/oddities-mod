package net.seep.odd.abilities.power;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.seep.odd.Oddities;
import net.seep.odd.abilities.lunar.entity.LunarMarkProjectileEntity;
import net.seep.odd.abilities.lunar.net.LunarPackets;

import java.util.Map;
import java.util.UUID;

public final class LunarPower implements Power {
    @Override public String id() { return "lunar"; }
    @Override public boolean hasSlot(String slot) {
        return "primary".equals(slot) || "secondary".equals(slot) || "third".equals(slot);
    }

    @Override public long cooldownTicks() { return 0; }               // primary toggle
    @Override public long secondaryCooldownTicks() { return 20 * 25; } // Moonlight throw cooldown (normal system cooldown)
    @Override public long thirdCooldownTicks() { return 20; }         // Remove Moonlight (short cd)

    @Override
    public Identifier iconTexture(String slot) {
        return switch (slot) {
            case "primary"   -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/lunar_tether.png");
            case "secondary" -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/lunar_moonlight.png");
            // Keep existing texture path to avoid missing-asset errors (was burst icon)
            case "third"     -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/lunar_burst.png");
            default          -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/ability_default.png");
        };
    }

    @Override public String slotTitle(String slot) {
        return switch (slot) {
            case "primary" -> "TETHER";
            case "secondary" -> "MOONLIGHT";
            case "third" -> "REMOVE";
            default -> Power.super.slotTitle(slot);
        };
    }

    @Override public String longDescription() {
        return "Throw a Moonlight projectile; it anchors on first hit (entity or block). Primary toggles continuous tether (drains charge). Third removes the current Moonlight mark.";
    }

    @Override public String slotLongDescription(String slot) {
        return switch (slot) {
            case "primary"   -> "Toggle continuous pull toward the Moonlight mark. Drains tether charge.";
            case "secondary" -> "Throw Moonlight. If a mark already exists, it is replaced by the new throw.";
            case "third"     -> "Remove the current Moonlight mark.";
            default -> "";
        };
    }

    @Override public Identifier portraitTexture() { return new Identifier(Oddities.MOD_ID, "textures/gui/overview/lunar.png"); }

    /* ==================== Per-player state ==================== */
    private static final class St {
        boolean tetherOn = false;
        Anchor anchor = null;

        // tether charge
        float energy = MAX_ENERGY;
        boolean outNotified = false;
        int syncTimer = 0;
    }
    private static final Map<UUID, St> DATA = new Object2ObjectOpenHashMap<>();
    private static St S(PlayerEntity p) { return DATA.computeIfAbsent(p.getUuid(), u -> new St()); }

    /* Anchor can be pinned to a block (pos) or to a living entity (target UUID). */
    private static final class Anchor {
        final BlockPos pos; final UUID target;
        Anchor(BlockPos pos) { this.pos = pos; this.target = null; }
        Anchor(UUID target)  { this.pos = null; this.target = target; }
        boolean valid(ServerWorld w) {
            if (pos != null) return true;
            Entity e = w.getEntity(target);
            return e instanceof LivingEntity le && le.isAlive();
        }
        Vec3d point(ServerWorld w) {
            if (pos != null) return Vec3d.ofCenter(pos);
            Entity e = w.getEntity(target);
            return (e == null) ? null : e.getPos().add(0, e.getStandingEyeHeight()*0.6, 0);
        }
    }

    /* ==================== Tether charge config ==================== */
    private static final float MAX_ENERGY        = 100f;
    private static final float DRAIN_PER_TICK    = 0.55f; // while tetherOn
    private static final float RECHARGE_PER_TICK = 0.30f; // when not tethering

    /* ==================== Slot actions ==================== */

    /** PRIMARY: toggle continuous tether (requires charge). */
    @Override public void activate(ServerPlayerEntity p) {
        var st = S(p);
        if (!st.tetherOn) {
            if (st.energy <= 0f) {
                p.sendMessage(Text.literal("Tether: no charge"), true);
                return;
            }
            st.outNotified = false;
            st.tetherOn = true;
            p.sendMessage(Text.literal("Tether: ON"), true);
        } else {
            st.tetherOn = false;
            p.sendMessage(Text.literal("Tether: OFF"), true);
        }
        LunarPackets.syncTether(p, st.energy, MAX_ENERGY, st.tetherOn);
    }

    /**
     * SECONDARY: Moonlight — ALWAYS throw.
     * If a mark already exists, delete it first, then throw a new Moonlight.
     * Cooldown is handled by the normal Power cooldown system (secondaryCooldownTicks()).
     */
    @Override public void activateSecondary(ServerPlayerEntity p) {
        var st = S(p);

        if (st.anchor != null) {
            clearAnchor(p);
            // do NOT message spam here; we’ll just replace cleanly
        }

        // spawn the dedicated projectile — it will set the anchor on collision
        ServerWorld sw = (ServerWorld)p.getWorld();
        var proj = new LunarMarkProjectileEntity(sw, p);
        Vec3d look = p.getRotationVec(1f).normalize();
        proj.setVelocity(look.x, look.y, look.z, 1.25f, 0.0f);
        proj.setNoGravity(false);
        proj.refreshPositionAndAngles(p.getX(), p.getEyeY() - 0.1, p.getZ(), p.getYaw(), p.getPitch());
        sw.spawnEntity(proj);

        p.swingHand(Hand.MAIN_HAND, true);
        sw.playSound(null, p.getX(), p.getY(), p.getZ(),
                SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.6f, 1.2f);
        p.sendMessage(Text.literal("Moonlight →"), true);
    }

    /**
     * THIRD: REMOVE MOONLIGHT — clears the current anchor (no pull / no burst).
     * Has its own normal cooldown (thirdCooldownTicks()).
     */
    @Override public void activateThird(ServerPlayerEntity p) {
        var st = S(p);
        if (st.anchor == null) {
            p.sendMessage(Text.literal("No Moonlight mark."), true);
            return;
        }
        clearAnchor(p);
        p.sendMessage(Text.literal("Moonlight removed."), true);
    }

    /* ==================== Called by projectile on server when it collides ==================== */
    public static void setAnchorPosFromProjectile(ServerPlayerEntity p, BlockPos pos) {
        var st = S(p);
        st.anchor = new Anchor(pos.toImmutable());
        fxAnchor((ServerWorld)p.getWorld(), Vec3d.ofCenter(pos));
        LunarPackets.sendAnchorPos(p, pos);
        p.sendMessage(Text.literal("Moonlight mark anchored."), true);
    }
    public static void setAnchorEntityFromProjectile(ServerPlayerEntity p, LivingEntity target) {
        var st = S(p);
        st.anchor = new Anchor(target.getUuid());
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 20*30, 0, true, false)); // visual
        fxAnchor((ServerWorld)p.getWorld(), target.getPos().add(0, target.getStandingEyeHeight()*0.6, 0));
        LunarPackets.sendAnchorEntity(p, target.getId());
        p.sendMessage(Text.literal("Moonlight mark attached to " + target.getName().getString()), true);
    }

    private static void clearAnchor(ServerPlayerEntity p) {
        var st = S(p);
        if (st.anchor != null && st.anchor.target != null) {
            Entity e = ((ServerWorld)p.getWorld()).getEntity(st.anchor.target);
            if (e instanceof LivingEntity le) le.removeStatusEffect(StatusEffects.GLOWING);
        }
        st.anchor = null;
        // DO NOT auto-toggle tether; user controls it. Just stop movement effect when no anchor.
        LunarPackets.clearAnchor(p);
    }

    private static void fxAnchor(ServerWorld sw, Vec3d at) {
        sw.spawnParticles(ParticleTypes.GLOW, at.x, at.y, at.z, 10, 0.2,0.2,0.2, 0.01);
        sw.spawnParticles(ParticleTypes.WAX_OFF, at.x, at.y-0.2, at.z, 12, 0.4,0,0.4, 0.0);
        sw.playSound(null, at.x, at.y, at.z,
                SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 0.8f, 1.2f);
    }

    /* ==================== Tether physics + charge drain ==================== */
    private static final double TETHER_ACCEL = 0.12;
    private static final double MAX_SPEED    = 1.25;
    private static final double DEADZONE     = 1.5; // near anchor, don't accelerate, but don't toggle off

    private static void serverTickPlayer(ServerPlayerEntity p) {
        var st = S(p);

        // charge logic
        boolean consuming = (st.tetherOn && st.anchor != null);
        if (consuming) {
            st.energy = Math.max(0f, st.energy - DRAIN_PER_TICK);
            if (st.energy == 0f && !st.outNotified) {
                st.outNotified = true;
                p.sendMessage(Text.literal("Tether: out of charge"), true);
            }
        } else {
            st.energy = Math.min(MAX_ENERGY, st.energy + RECHARGE_PER_TICK);
            if (st.energy > 5f) st.outNotified = false;
        }

        // movement only if we have an anchor AND tether is on AND we have charge
        if (st.anchor == null) { syncMaybe(p, st); return; }
        ServerWorld sw = (ServerWorld)p.getWorld();
        if (!st.anchor.valid(sw)) { clearAnchor(p); syncMaybe(p, st); return; }

        boolean pulling = (st.tetherOn && st.energy > 0f);
        if (!pulling) { syncMaybe(p, st); return; }

        Vec3d target = st.anchor.point(sw);
        if (target == null) { clearAnchor(p); syncMaybe(p, st); return; }

        Vec3d pos = p.getPos().add(0, p.getStandingEyeHeight()*0.4, 0);
        Vec3d to  = target.subtract(pos);
        double dist = to.length();

        if (dist > DEADZONE) {
            Vec3d dir = to.normalize();
            Vec3d v   = p.getVelocity().add(dir.multiply(TETHER_ACCEL));

            // clamp horizontal speed
            Vec3d hor = new Vec3d(v.x, 0, v.z);
            double hlen = hor.length();
            if (hlen > MAX_SPEED) {
                hor = hor.normalize().multiply(MAX_SPEED);
                v = new Vec3d(hor.x, v.y, hor.z);
            }

            // mild lift & sink clamp
            v = new Vec3d(v.x, MathHelper.clamp(v.y + 0.02, -0.30, 0.55), v.z);

            p.setVelocity(v);
            p.velocityModified = true;
            p.fallDistance = 0f;
        }

        if ((p.age & 1) == 0)
            sw.spawnParticles(ParticleTypes.END_ROD, p.getX(), p.getY()+0.5, p.getZ(), 1, 0.1,0.1,0.1, 0.0);

        syncMaybe(p, st);
    }

    private static void syncMaybe(ServerPlayerEntity p, St st) {
        if (++st.syncTimer >= 5) { // throttle
            st.syncTimer = 0;
            LunarPackets.syncTether(p, st.energy, MAX_ENERGY, st.tetherOn);
        }
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) serverTickPlayer(p);
        });
    }

    /* ==================== Client HUD: tether charge bar ==================== */
    @Environment(EnvType.CLIENT)
    public static final class Hud {
        // updated via packet:
        private static float energy = MAX_ENERGY, max = MAX_ENERGY;
        private static boolean on = false;

        /** Called from client packet handler. */
        public static void setClientTether(float e, float m, boolean enabled) {
            energy = e; max = m; on = enabled;
        }

        public static void init() {
            HudRenderCallback.EVENT.register((DrawContext ctx, float td) -> {
                var mc = MinecraftClient.getInstance();
                if (mc == null || mc.player == null) return;
                if (!on) return; // only show while tether is toggled on

                int sw = mc.getWindow().getScaledWidth();
                int sh = mc.getWindow().getScaledHeight();

                int barW = 120, barH = 9;
                int x = sw/2 - barW/2;
                int y = sh - 48;

                // background (vanilla-ish dark)
                ctx.fill(x-2, y-2, x+barW+2, y+barH+2, 0x88000000);
                ctx.fill(x, y, x+barW, y+barH, 0xCC1C1C1C);

                float pct = Math.max(0f, Math.min(1f, energy / Math.max(1f, max)));
                int fillW = Math.round(barW * pct);

                // color: green → yellow → red
                int col = pct > 0.5f ? 0xFF7EE08F : (pct > 0.2f ? 0xFFFFCF6A : 0xFFFF6B6B);
                ctx.fill(x, y, x + fillW, y + barH, col);

                String label = "Tether " + Math.round(energy) + "/" + Math.round(max);
                int tw = mc.textRenderer.getWidth(label);
                ctx.drawText(mc.textRenderer, label, x + (barW - tw)/2, y - 10, 0xFFF0F0F0, true);
            });

            WorldRenderEvents.START.register((WorldRenderContext ignored) -> {});
        }
    }
}
