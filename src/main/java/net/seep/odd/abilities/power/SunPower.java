package net.seep.odd.abilities.power;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.seep.odd.Oddities;
import net.seep.odd.abilities.PowerAPI;
import net.seep.odd.abilities.data.CooldownState;
import net.seep.odd.abilities.net.PowerNetworking;
import net.seep.odd.abilities.rat.PehkuiUtil;
import net.seep.odd.abilities.sun.SunFxNet;
import net.seep.odd.abilities.sun.SunNet;
import net.seep.odd.entity.ModEntities;
import net.seep.odd.entity.sun.PocketSunEntity;
import net.seep.odd.status.ModStatusEffects;

import java.util.Map;
import java.util.UUID;

public final class SunPower implements Power, DeferredCooldownPower {

    public static final SunPower INSTANCE = new SunPower();

    public static final int SECONDARY_MAX_HOLD_TICKS = 20 * 8;
    public static final int SECONDARY_COOLDOWN_TICKS = 20 * 8;

    private static final float MAX_ENERGY = 100.0f;
    private static final float DAY_CHARGE_PER_TICK = 0.09f;
    private static final float TRANSFORM_DRAIN_PER_TICK = 0.28f;
    private static final float MIN_ENERGY_TO_TRANSFORM = 18.0f;

    private static final float TRANSFORM_SCALE = 1.65f;
    private static final float TRANSFORM_MOTION = 0.82f;
    private static final float TRANSFORM_EYE = 1.28f;

    private static final double TRANSFORM_MAX_HEALTH = 36.0; // 20 + 16 = +8 hearts
    private static final double NORMAL_MAX_HEALTH = 20.0;
    private static final double TRANSFORM_KB_RESIST = 0.85;

    private static final int EFFECT_REFRESH_TICKS = 8;

    @Override public String id() { return "sun"; }
    @Override public boolean hasSlot(String slot) { return "primary".equals(slot) || "secondary".equals(slot); }
    @Override public long cooldownTicks() { return 0; }
    @Override public long secondaryCooldownTicks() { return SECONDARY_COOLDOWN_TICKS; }
    @Override public boolean deferPrimaryCooldown() { return false; }
    @Override public boolean deferSecondaryCooldown() { return true; }
    @Override public boolean deferThirdCooldown() { return false; }

    @Override
    public Identifier iconTexture(String slot) {
        return switch (slot) {
            case "primary" -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/sun_transform.png");
            case "secondary" -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/sun_pocket_sun.png");
            default -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/ability_default.png");
        };
    }

    @Override
    public String longDescription() {
        return "Charge sunlight while under the open daytime sky. Primary toggles a giant radiant form. Secondary holds and throws a growing pocket sun.";
    }

    @Override
    public String slotTitle(String slot) {
        return switch (slot) {
            case "primary" -> "TRANSFORM";
            case "secondary" -> "POCKET SUN";
            default -> Power.super.slotTitle(slot);
        };
    }

    @Override
    public String slotLongDescription(String slot) {
        return switch (slot) {
            case "primary" -> "Toggle a larger empowered form that drains your sunlight bar.";
            case "secondary" -> "Hold to grow a pocket sun for up to 8 seconds, then throw it on release.";
            default -> "Sun";
        };
    }

    @Override
    public Identifier portraitTexture() {
        return new Identifier(Oddities.MOD_ID, "textures/gui/overview/sun.png");
    }

    private static final class State {
        boolean transformed = false;
        boolean appliedTransformStats = false;
        boolean secondaryCharging = false;
        int secondaryHeldTicks = 0;
        UUID heldSunId = null;
        float energy = 15.0f;

        int hudSyncCooldown = 0;
        int lastSentEnergy = Integer.MIN_VALUE;
        boolean lastSentTransformed = false;
        boolean lastSentSecondary = false;
    }

    private static final Map<UUID, State> DATA = new Object2ObjectOpenHashMap<>();
    private static State S(ServerPlayerEntity p) { return DATA.computeIfAbsent(p.getUuid(), u -> new State()); }

    private static final Object2LongOpenHashMap<UUID> WARN_UNTIL = new Object2LongOpenHashMap<>();

    private static boolean isCurrent(ServerPlayerEntity p) {
        Power pow = Powers.get(PowerAPI.get(p));
        return pow instanceof SunPower;
    }

    private static boolean isPowerless(ServerPlayerEntity p) {
        return p != null && p.hasStatusEffect(ModStatusEffects.POWERLESS);
    }

    private static void warnOncePerSec(ServerPlayerEntity p, String msg) {
        long now = p.getWorld().getTime();
        long nextOk = WARN_UNTIL.getOrDefault(p.getUuid(), 0L);
        if (now < nextOk) return;
        WARN_UNTIL.put(p.getUuid(), now + 20);
        p.sendMessage(Text.literal(msg), true);
    }

    @Override
    public void forceDisable(ServerPlayerEntity p) {
        if (p == null) return;
        State st = DATA.get(p.getUuid());
        if (st == null) return;
        stopSecondaryCharge(p, st, true);
        if (st.transformed) {
            toggleTransformOff(p, st, true);
        }
        SunFxNet.sendEmpoweredOverlay(p, false);
        SunNet.sendHoldPose(p, false);
    }

    @Override
    public void activate(ServerPlayerEntity p) {
        if (!isCurrent(p)) return;
        if (!(p.getWorld() instanceof ServerWorld sw)) return;
        State st = S(p);

        if (isPowerless(p)) {
            warnOncePerSec(p, "§cYou are powerless.");
            stopSecondaryCharge(p, st, true);
            if (st.transformed) toggleTransformOff(p, st, true);
            return;
        }

        if (st.secondaryCharging) {
            releasePocketSun(p, st);
            return;
        }

        if (st.transformed) {
            toggleTransformOff(p, st, false);
            return;
        }

        if (st.energy < MIN_ENERGY_TO_TRANSFORM) {
            warnOncePerSec(p, "§eNot enough sunlight.");
            return;
        }

        toggleTransformOn(sw, p, st);
    }

    @Override
    public void activateSecondary(ServerPlayerEntity p) {
        if (!isCurrent(p)) return;
        if (isPowerless(p)) {
            warnOncePerSec(p, "§cYou are powerless.");
            return;
        }
        State st = S(p);
        if (st.secondaryCharging) {
            releasePocketSun(p, st);
            return;
        }
        secondaryHoldStart(p);
    }

    public static void secondaryHoldStart(ServerPlayerEntity p) {
        if (!isCurrent(p) || !(p.getWorld() instanceof ServerWorld sw)) return;
        State st = S(p);

        if (isPowerless(p)) {
            warnOncePerSec(p, "§cYou are powerless.");
            return;
        }

        if (PowerAPI.getRemainingCooldownTicks(p, "secondary") > 0) return;
        if (st.secondaryCharging) return;

        PowerAPI.beginUse(p, "secondary");
        PowerAPI.setHeld(p, "secondary", true);

        st.secondaryCharging = true;
        st.secondaryHeldTicks = 0;

        PocketSunEntity held = new PocketSunEntity(ModEntities.POCKET_SUN, sw);
        held.setOwner(p);
        held.setHeldVisual(true);
        held.setArmed(false);
        held.setChargeProgress(0.0f);
        held.setNoClipState(true);
        positionHeldSun(p, held);
        sw.spawnEntity(held);
        st.heldSunId = held.getUuid();

        SunNet.sendHoldPose(p, true);
    }

    public static void secondaryHoldEnd(ServerPlayerEntity p) {
        if (!isCurrent(p)) return;
        State st = S(p);
        if (!st.secondaryCharging) return;
        releasePocketSun(p, st);
    }

    public static void serverTick(ServerPlayerEntity p) {
        if (!isCurrent(p) || !(p.getWorld() instanceof ServerWorld sw)) return;
        State st = S(p);

        if (isPowerless(p)) {
            stopSecondaryCharge(p, st, true);
            if (st.transformed) toggleTransformOff(p, st, true);
            warnOncePerSec(p, "§cYou are powerless.");
            syncHudIfNeeded(p, st, true);
            return;
        }

        boolean outdoors = canChargeInSun(p);
        if (outdoors && !st.transformed) {
            st.energy = Math.min(MAX_ENERGY, st.energy + DAY_CHARGE_PER_TICK);
        }

        if (st.transformed) {
            if (!st.appliedTransformStats) {
                applyTransformStats(p, st);
            }

            st.energy = Math.max(0.0f, st.energy - TRANSFORM_DRAIN_PER_TICK);
            refreshTransformEffects(p);

            if (st.energy <= 0.001f) {
                toggleTransformOff(p, st, false);
            }
        }

        if (st.secondaryCharging) {
            PocketSunEntity held = getHeldSun(sw, st);
            if (held == null) {
                stopSecondaryCharge(p, st, true);
            } else {
                st.secondaryHeldTicks++;
                float progress = MathHelper.clamp(st.secondaryHeldTicks / (float) SECONDARY_MAX_HOLD_TICKS, 0.0f, 1.0f);
                positionHeldSun(p, held);
                held.setChargeProgress(progress);
                held.setHeldVisual(true);
                held.setArmed(false);
                held.setNoClipState(true);

                if (st.secondaryHeldTicks >= SECONDARY_MAX_HOLD_TICKS) {
                    releasePocketSun(p, st);
                }
            }
        }

        syncHudIfNeeded(p, st, false);
    }

    public static void onDeactivated(ServerPlayerEntity p) {
        State st = S(p);
        stopSecondaryCharge(p, st, true);
        if (st.transformed) {
            toggleTransformOff(p, st, true);
        }
        st.energy = MathHelper.clamp(st.energy, 0.0f, MAX_ENERGY);
    }

    private static boolean canChargeInSun(ServerPlayerEntity p) {
        if (p == null || p.isSpectator()) return false;
        if (!p.getWorld().isDay()) return false;
        if (p.getWorld().isRaining()) return false;

        var pos = p.getBlockPos();
        int topY = p.getWorld().getTopY(Heightmap.Type.MOTION_BLOCKING, pos.getX(), pos.getZ());
        if (topY > pos.getY() + 1) return false;
        return p.getWorld().isSkyVisible(pos.up());
    }

    private static void toggleTransformOn(ServerWorld sw, ServerPlayerEntity p, State st) {
        st.transformed = true;
        st.appliedTransformStats = false;
        applyTransformStats(p, st);
        refreshTransformEffects(p);
        SunFxNet.sendEmpoweredOverlay(p, true);
        SunFxNet.broadcastTransformRay(p, false);
        sw.playSound(null, p.getX(), p.getY(), p.getZ(),
                net.minecraft.sound.SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT,
                net.minecraft.sound.SoundCategory.PLAYERS, 0.8f, 1.35f);
    }

    private static void toggleTransformOff(ServerPlayerEntity p, State st, boolean silent) {
        st.transformed = false;
        removeTransformStats(p, st);
        SunFxNet.sendEmpoweredOverlay(p, false);
        if (!silent) {
            SunFxNet.broadcastTransformRay(p, true);
            p.getWorld().playSound(null, p.getX(), p.getY(), p.getZ(),
                    net.minecraft.sound.SoundEvents.BLOCK_BEACON_DEACTIVATE,
                    net.minecraft.sound.SoundCategory.PLAYERS, 0.7f, 1.4f);
        }
    }

    private static void applyTransformStats(ServerPlayerEntity p, State st) {
        st.appliedTransformStats = true;

        var maxHealth = p.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(TRANSFORM_MAX_HEALTH);
            if (p.getHealth() < 20.0f) {
                p.setHealth(Math.min((float) TRANSFORM_MAX_HEALTH, p.getHealth() + 16.0f));
            }
        }

        var kbRes = p.getAttributeInstance(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE);
        if (kbRes != null) kbRes.setBaseValue(TRANSFORM_KB_RESIST);

        PehkuiUtil.applyScaleSafely(p, TRANSFORM_SCALE, TRANSFORM_MOTION, 1.0f, 1.0f, TRANSFORM_EYE);
    }

    private static void removeTransformStats(ServerPlayerEntity p, State st) {
        if (!st.appliedTransformStats) return;
        st.appliedTransformStats = false;

        var maxHealth = p.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(NORMAL_MAX_HEALTH);
            if (p.getHealth() > NORMAL_MAX_HEALTH) p.setHealth((float) NORMAL_MAX_HEALTH);
        }

        var kbRes = p.getAttributeInstance(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE);
        if (kbRes != null) kbRes.setBaseValue(0.0);

        PehkuiUtil.resetScalesSafely(p);
    }

    private static void refreshTransformEffects(ServerPlayerEntity p) {
        if ((p.age & 7) != 0) return;
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, EFFECT_REFRESH_TICKS + 4, 1, true, false, false));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, EFFECT_REFRESH_TICKS + 4, 0, true, false, false));
    }

    private static void releasePocketSun(ServerPlayerEntity p, State st) {
        if (!(p.getWorld() instanceof ServerWorld sw)) return;
        PocketSunEntity held = getHeldSun(sw, st);
        if (held == null) {
            stopSecondaryCharge(p, st, true);
            return;
        }

        float progress = MathHelper.clamp(st.secondaryHeldTicks / (float) SECONDARY_MAX_HOLD_TICKS, 0.0f, 1.0f);

        held.setHeldVisual(false);
        held.setArmed(true);
        held.setNoClipState(false);
        held.setChargeProgress(progress);

        Vec3d dir = p.getRotationVec(1.0f).normalize();
        double speed = 0.72 + (0.34 * progress);
        held.launch(dir, speed);

        stopSecondaryChargeStateOnly(p, st);
        startCooldown(p, "secondary", SECONDARY_COOLDOWN_TICKS);
    }

    private static void stopSecondaryCharge(ServerPlayerEntity p, State st, boolean discardEntity) {
        if (p == null || st == null) return;
        if (discardEntity && p.getWorld() instanceof ServerWorld sw) {
            PocketSunEntity held = getHeldSun(sw, st);
            if (held != null) held.discard();
        }
        stopSecondaryChargeStateOnly(p, st);
    }

    private static void stopSecondaryChargeStateOnly(ServerPlayerEntity p, State st) {
        st.secondaryCharging = false;
        st.secondaryHeldTicks = 0;
        st.heldSunId = null;
        PowerAPI.setHeld(p, "secondary", false);
        SunNet.sendHoldPose(p, false);
    }

    private static PocketSunEntity getHeldSun(ServerWorld sw, State st) {
        if (st.heldSunId == null) return null;
        Entity e = sw.getEntity(st.heldSunId);
        return e instanceof PocketSunEntity ps ? ps : null;
    }

    private static void positionHeldSun(ServerPlayerEntity p, PocketSunEntity held) {
        Vec3d look = p.getRotationVec(1.0f).normalize();
        Vec3d right = new Vec3d(-look.z, 0.0, look.x).normalize();
        Vec3d base = p.getEyePos()
                .add(look.multiply(1.15))
                .add(right.multiply(0.34))
                .add(0.0, -0.32, 0.0);

        held.refreshPositionAndAngles(base.x, base.y, base.z, p.getYaw(), p.getPitch());
        held.freezeInPlace();
    }

    private static void syncHudIfNeeded(ServerPlayerEntity p, State st, boolean force) {
        if (!force && st.hudSyncCooldown-- > 0) return;
        st.hudSyncCooldown = 4;

        int energyInt = Math.round(st.energy);
        if (!force
                && energyInt == st.lastSentEnergy
                && st.transformed == st.lastSentTransformed
                && st.secondaryCharging == st.lastSentSecondary) {
            return;
        }

        st.lastSentEnergy = energyInt;
        st.lastSentTransformed = st.transformed;
        st.lastSentSecondary = st.secondaryCharging;
        SunNet.sendHudState(p, st.energy, st.transformed, st.secondaryCharging);
    }

    private static void startCooldown(ServerPlayerEntity player, String slot, int cooldownTicks) {
        String id = PowerAPI.get(player);
        if (id == null || id.isEmpty()) return;

        long now = player.getWorld().getTime();
        String key = id;
        String lane = "primary";

        switch (slot) {
            case "secondary" -> {
                key = id + "#secondary";
                lane = "secondary";
            }
            case "third" -> {
                key = id + "#third";
                lane = "third";
            }
            case "fourth" -> {
                key = id + "#fourth";
                lane = "fourth";
            }
            default -> {
            }
        }

        CooldownState.get(player.getServer()).setLastUse(player.getUuid(), key, now);
        PowerNetworking.sendCooldown(player, lane, Math.max(0, cooldownTicks));
    }

    @Environment(EnvType.CLIENT)
    public static final class Client {
        private static float hudEnergy = 0.0f;
        private static boolean hudTransformed = false;
        private static boolean hudSecondary = false;

        public static void init() {
            HudRenderCallback.EVENT.register((DrawContext ctx, float tickDelta) -> {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.player == null) return;

                if (hudEnergy <= 0.01f && !hudTransformed && !hudSecondary) return;

                int sw = mc.getWindow().getScaledWidth();
                int sh = mc.getWindow().getScaledHeight();

                int w = 90;
                int h = 9;
                int x = sw / 2 - w / 2;
                int y = sh - 58;

                ctx.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xAA1E1200);
                ctx.fill(x, y, x + w, y + h, 0xBB3A2100);

                int fill = MathHelper.clamp(Math.round((hudEnergy / MAX_ENERGY) * w), 0, w);
                int left = 0xFFF8D24A;
                int right = 0xFFFF9328;

                for (int i = 0; i < fill; i++) {
                    float t = fill <= 1 ? 0.0f : i / (float) (fill - 1);
                    int color = lerpColor(left, right, t);
                    ctx.fill(x + i, y, x + i + 1, y + h, color);
                }

                if (hudTransformed) {
                    ctx.drawText(mc.textRenderer, Text.literal("SUNFORM"), x + 2, y - 10, 0xFFFCE07A, false);
                } else if (hudSecondary) {
                    ctx.drawText(mc.textRenderer, Text.literal("POCKET SUN"), x + 2, y - 10, 0xFFFFB647, false);
                }
            });
        }

        public static void setHud(float energy, boolean transformed, boolean secondaryCharging) {
            hudEnergy = energy;
            hudTransformed = transformed;
            hudSecondary = secondaryCharging;
        }

        private static int lerpColor(int a, int b, float t) {
            int aa = (a >>> 24) & 0xFF;
            int ar = (a >>> 16) & 0xFF;
            int ag = (a >>> 8) & 0xFF;
            int ab = a & 0xFF;

            int ba = (b >>> 24) & 0xFF;
            int br = (b >>> 16) & 0xFF;
            int bg = (b >>> 8) & 0xFF;
            int bb = b & 0xFF;

            int ca = Math.round(MathHelper.lerp(t, aa, ba));
            int cr = Math.round(MathHelper.lerp(t, ar, br));
            int cg = Math.round(MathHelper.lerp(t, ag, bg));
            int cb = Math.round(MathHelper.lerp(t, ab, bb));

            return (ca << 24) | (cr << 16) | (cg << 8) | cb;
        }
    }

    static {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!(player instanceof ServerPlayerEntity sp) || !isCurrent(sp)) return ActionResult.PASS;
            State st = S(sp);
            if (!st.transformed) return ActionResult.PASS;

            if (entity instanceof LivingEntity le) {
                Vec3d kb = sp.getRotationVec(1.0f).normalize().multiply(1.18).add(0.0, 0.12, 0.0);
                le.addVelocity(kb.x, kb.y, kb.z);
                le.velocityModified = true;
            }

            return ActionResult.PASS;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (player instanceof ServerPlayerEntity sp && isCurrent(sp) && S(sp).secondaryCharging) {
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (player instanceof ServerPlayerEntity sp && isCurrent(sp) && S(sp).secondaryCharging) {
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.player.getUuid();
            DATA.remove(id);
            WARN_UNTIL.removeLong(id);
        });
    }
}