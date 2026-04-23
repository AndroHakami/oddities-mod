package net.seep.odd.abilities.power;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.abilities.PowerAPI;
import net.seep.odd.abilities.data.CooldownState;
import net.seep.odd.abilities.net.PowerNetworking;
import net.seep.odd.abilities.shift.ShiftFxNet;
import net.seep.odd.abilities.shift.ShiftNet;
import net.seep.odd.abilities.shift.entity.DecoyEntity;
import net.seep.odd.entity.ModEntities;
import net.seep.odd.sound.ModSounds;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class ShiftPower implements Power, DeferredCooldownPower, SecondaryDuringCooldown {
    public static final ShiftPower INSTANCE = new ShiftPower();

    public static final int PRIMARY_SWAP_COOLDOWN_TICKS = 20 * 2;
    public static final int SECONDARY_COOLDOWN_TICKS    = 20 * 20;
    public static final int TAG_DURATION_TICKS          = 20 * 20;
    public static final int DECOY_RUN_TICKS             = 20 * 4;
    public static final int DECOY_MAX_LIFE_TICKS        = 20 * 20;

    /** 50 hearts. */
    public static final double PULL_ONLY_MAX_HEALTH = 100.0D;

    public ShiftPower() {}

    @Override public String id() { return "shift"; }
    @Override public boolean hasSlot(String slot) { return "primary".equals(slot) || "secondary".equals(slot); }
    @Override public long cooldownTicks() { return PRIMARY_SWAP_COOLDOWN_TICKS; }
    @Override public long secondaryCooldownTicks() { return SECONDARY_COOLDOWN_TICKS; }

    @Override public boolean deferPrimaryCooldown() { return true; }
    @Override public boolean deferSecondaryCooldown() { return true; }
    @Override public boolean deferThirdCooldown() { return false; }

    @Override
    public Identifier iconTexture(String slot) {
        return switch (slot) {
            case "primary" -> new Identifier("odd", "textures/gui/abilities/shift_clap.png");
            case "secondary" -> new Identifier("odd", "textures/gui/abilities/shift_decoy.png");
            default -> new Identifier("odd", "textures/gui/abilities/ability_default.png");
        };
    }

    @Override
    public Identifier portraitTexture() {
        return new Identifier("odd", "textures/gui/overview/max_portrait.png");
    }

    @Override
    public String longDescription() {
        return "Phase into a vibrating chromatic state, mark a victim with a strike, then fold space to trade places with them. Summon a decoy to sprint ahead and blink to it.";
    }

    @Override
    public String slotTitle(String slot) {
        return switch (slot) {
            case "primary" -> "SHIFT";
            case "secondary" -> "DECOY";
            default -> Power.super.slotTitle(slot);
        };
    }

    @Override
    public String slotLongDescription(String slot) {
        return switch (slot) {
            case "primary" -> "Press once to become imbued. Hit a living target to lock them for 20 seconds, then press again to swap every 2 seconds. Press again before tagging to cancel.";
            case "secondary" -> "Summon a decoy that runs forward for 4 seconds, then idles. Press again while it exists to teleport to it. Shooting the decoy reveals the attacker.";
            default -> "Shift";
        };
    }

    private static final class State {
        boolean imbued = false;
        @Nullable UUID targetUuid = null;
        long targetExpiresAt = 0L;
        boolean pullOnly = false;

        @Nullable UUID decoyUuid = null;
    }

    private static final Object2ObjectOpenHashMap<UUID, State> DATA = new Object2ObjectOpenHashMap<>();
    private static final Object2LongOpenHashMap<UUID> WARN_UNTIL = new Object2LongOpenHashMap<>();

    private static State S(ServerPlayerEntity player) {
        return DATA.computeIfAbsent(player.getUuid(), id -> new State());
    }

    private static boolean isCurrent(ServerPlayerEntity player) {
        Power power = Powers.get(PowerAPI.get(player));
        return power instanceof ShiftPower;
    }

    @Override
    public void activate(ServerPlayerEntity player) {
        if (!isCurrent(player)) return;

        State st = S(player);

        if (!st.imbued) {
            PowerAPI.beginUse(player, "primary");
            st.imbued = true;
            st.targetUuid = null;
            st.targetExpiresAt = 0L;
            st.pullOnly = false;
            ShiftFxNet.sendImbue(player, true);
            player.sendMessage(Text.literal("§bShift energy humming."), true);
            return;
        }

        if (st.targetUuid == null) {
            resetPrimary(player, st, true);
            player.sendMessage(Text.literal("§7Shift canceled."), true);
            return;
        }

        if (PowerAPI.getRemainingCooldownTicks(player, "primary") > 0) {
            return;
        }

        LivingEntity target = getTargetEntity(player, st);
        if (target == null) {
            resetPrimary(player, st, false);
            return;
        }

        performSwap(player, target, st.pullOnly);
        startCooldown(player, "primary", PRIMARY_SWAP_COOLDOWN_TICKS);
    }

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        if (!isCurrent(player)) return;

        State st = S(player);
        DecoyEntity decoy = getDecoy(player, st);

        if (decoy != null) {
            teleportEntity(player, decoy.getPos(), decoy.getYaw(), decoy.getPitch());
            decoy.discard();
            st.decoyUuid = null;
            player.getServerWorld().playSound(
                    null,
                    player.getX(), player.getY(), player.getZ(),
                    ModSounds.SHIFT_CLAP,
                    player.getSoundCategory(),
                    1.0F, 1.0F
            );
            ShiftFxNet.sendPulse(player, 0.90F);
            return;
        }

        if (PowerAPI.getRemainingCooldownTicks(player, "secondary") > 0) {
            return;
        }

        DecoyEntity spawned = ModEntities.DECOY.create(player.getServerWorld());
        if (spawned == null) return;

        Vec3d dir = player.getRotationVector();
        Vec3d flat = new Vec3d(dir.x, 0.0D, dir.z);
        if (flat.lengthSquared() < 1.0E-4D) {
            flat = Vec3d.fromPolar(0.0F, player.getYaw());
            flat = new Vec3d(flat.x, 0.0D, flat.z);
        }
        flat = flat.normalize();

        spawned.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), player.getYaw(), 0.0F);
        spawned.setOwner(player);
        spawned.startRunning(flat, DECOY_RUN_TICKS, DECOY_MAX_LIFE_TICKS);

        player.getServerWorld().spawnEntity(spawned);
        st.decoyUuid = spawned.getUuid();

        PowerAPI.beginUse(player, "secondary");
        startCooldown(player, "secondary", SECONDARY_COOLDOWN_TICKS);
    }

    @Override
    public void forceDisable(ServerPlayerEntity player) {
        State st = DATA.get(player.getUuid());
        if (st == null) return;

        resetPrimary(player, st, false);

        DecoyEntity decoy = getDecoy(player, st);
        if (decoy != null) {
            decoy.discard();
        }
        st.decoyUuid = null;
    }

    public static void serverTick(ServerPlayerEntity player) {
        if (!isCurrent(player)) return;

        State st = S(player);

        if (!player.isAlive()) {
            INSTANCE.forceDisable(player);
            return;
        }

        if (st.imbued && st.targetUuid != null) {
            if (player.getWorld().getTime() >= st.targetExpiresAt) {
                resetPrimary(player, st, false);
            } else if (getTargetEntity(player, st) == null) {
                resetPrimary(player, st, false);
            }
        }

        if (st.decoyUuid != null && getDecoy(player, st) == null) {
            st.decoyUuid = null;
        }
    }

    public static void tagTargetOnHit(ServerPlayerEntity attacker, Entity rawTarget) {
        if (!isCurrent(attacker)) return;
        State st = S(attacker);

        if (!st.imbued || st.targetUuid != null) return;
        if (!(rawTarget instanceof LivingEntity target)) return;
        if (!target.isAlive() || target == attacker) return;

        st.targetUuid = target.getUuid();
        st.targetExpiresAt = attacker.getWorld().getTime() + TAG_DURATION_TICKS;
        st.pullOnly = getMaxHealth(target) >= PULL_ONLY_MAX_HEALTH;

        if (target instanceof ServerPlayerEntity taggedPlayer && !st.pullOnly) {
            ShiftFxNet.sendTagged(taggedPlayer, true);
        }

        attacker.sendMessage(
                Text.literal(st.pullOnly ? "§dHeavy target locked." : "§bTarget tagged."),
                true
        );
    }

    private static void performSwap(ServerPlayerEntity player, LivingEntity target, boolean pullOnly) {
        Vec3d playerPos = player.getPos();
        Vec3d targetPos = target.getPos();

        float playerYaw = player.getYaw();
        float playerPitch = player.getPitch();
        float targetYaw = target.getYaw();
        float targetPitch = target.getPitch();

        teleportEntity(player, targetPos, targetYaw, targetPitch);
        if (!pullOnly) {
            teleportEntity(target, playerPos, playerYaw, playerPitch);
        }

        ServerWorld world = player.getServerWorld();
        world.playSound(null, playerPos.x, playerPos.y, playerPos.z, ModSounds.SHIFT_CLAP, player.getSoundCategory(), 1.0F, 1.0F);
        world.playSound(null, targetPos.x, targetPos.y, targetPos.z, ModSounds.SHIFT_CLAP, player.getSoundCategory(), 1.0F, 1.0F);

        ShiftFxNet.sendPulse(player, 1.0F);
        if (target instanceof ServerPlayerEntity taggedPlayer) {
            ShiftFxNet.sendPulse(taggedPlayer, 1.0F);
        }
    }

    private static void resetPrimary(ServerPlayerEntity player, State st, boolean manualCancel) {
        clearTaggedVisual(player, st);
        st.imbued = false;
        st.targetUuid = null;
        st.targetExpiresAt = 0L;
        st.pullOnly = false;
        ShiftFxNet.sendImbue(player, false);
        if (!manualCancel) {
            player.sendMessage(Text.literal("§7Shift faded."), true);
        }
    }

    private static void clearTaggedVisual(ServerPlayerEntity player, State st) {
        if (st.targetUuid == null || st.pullOnly) return;
        Entity entity = player.getServerWorld().getEntity(st.targetUuid);
        if (entity instanceof ServerPlayerEntity taggedPlayer) {
            ShiftFxNet.sendTagged(taggedPlayer, false);
        }
    }

    @Nullable
    private static LivingEntity getTargetEntity(ServerPlayerEntity player, State st) {
        if (st.targetUuid == null) return null;
        Entity entity = player.getServerWorld().getEntity(st.targetUuid);
        return entity instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    @Nullable
    private static DecoyEntity getDecoy(ServerPlayerEntity player, State st) {
        if (st.decoyUuid == null) return null;
        Entity entity = player.getServerWorld().getEntity(st.decoyUuid);
        return entity instanceof DecoyEntity decoy && decoy.isAlive() ? decoy : null;
    }

    private static double getMaxHealth(LivingEntity living) {
        if (living.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH) != null) {
            return living.getAttributeValue(EntityAttributes.GENERIC_MAX_HEALTH);
        }
        return living.getMaxHealth();
    }

    private static void teleportEntity(Entity entity, Vec3d pos, float yaw, float pitch) {
        entity.stopRiding();

        if (entity instanceof ServerPlayerEntity player) {
            if (player.networkHandler != null) {
                player.networkHandler.requestTeleport(pos.x, pos.y, pos.z, yaw, pitch);
            } else {
                player.refreshPositionAndAngles(pos.x, pos.y, pos.z, yaw, pitch);
            }
        } else {
            entity.refreshPositionAndAngles(pos.x, pos.y, pos.z, yaw, pitch);
        }

        entity.setVelocity(Vec3d.ZERO);
        entity.velocityModified = true;

        if (entity instanceof LivingEntity living) {
            living.setHeadYaw(yaw);
            living.setBodyYaw(yaw);
        }
    }

    private static void warnOncePerSec(ServerPlayerEntity player, String msg) {
        long now = player.getWorld().getTime();
        long nextOk = WARN_UNTIL.getOrDefault(player.getUuid(), 0L);
        if (now < nextOk) return;
        WARN_UNTIL.put(player.getUuid(), now + 20L);
        player.sendMessage(Text.literal(msg), true);
    }

    static {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (player instanceof ServerPlayerEntity serverPlayer && isCurrent(serverPlayer)) {
                State st = S(serverPlayer);
                if (st.imbued && st.targetUuid == null) {
                    tagTargetOnHit(serverPlayer, entity);
                }
            }
            return ActionResult.PASS;
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.player;
            State st = DATA.get(player.getUuid());
            if (st != null) {
                clearTaggedVisual(player, st);
            }
            DATA.remove(player.getUuid());
            WARN_UNTIL.removeLong(player.getUuid());
        });
    }

    public static void registerNetworking() {
        ShiftNet.registerServer();
    }

    private static void startCooldown(ServerPlayerEntity player, String slot, int cooldownTicks) {
        String id = PowerAPI.get(player);
        if (id == null || id.isEmpty()) return;

        long now = player.getWorld().getTime();
        String key = id;
        String lane = "primary";

        switch (slot) {
            case "secondary" -> { key = id + "#secondary"; lane = "secondary"; }
            case "third" -> { key = id + "#third"; lane = "third"; }
            case "fourth" -> { key = id + "#fourth"; lane = "fourth"; }
            default -> { }
        }

        CooldownState.get(player.getServer()).setLastUse(player.getUuid(), key, now);
        PowerNetworking.sendCooldown(player, lane, Math.max(0, cooldownTicks));
    }
}
