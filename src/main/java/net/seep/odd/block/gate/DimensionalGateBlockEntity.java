// FILE: src/main/java/net/seep/odd/block/gate/DimensionalGateBlockEntity.java
package net.seep.odd.block.gate;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import net.seep.odd.Oddities;
import net.seep.odd.block.ModBlocks;

import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class DimensionalGateBlockEntity extends BlockEntity implements GeoBlockEntity {

    private static final boolean DEBUG = true;

    // =======================
    // TUNING (safe to tweak)
    // =======================
    /** MUST match whatever your portal FX uses for the visual plane push convention. */
    private static final double VISUAL_PUSH = 0.55;

    /** Trigger plane relative to the visual plane (negative = toward player). */
    private static final double TRIGGER_OFFSET_FROM_VISUAL = -0.10;

    /** Portal “width/height” scale vs the physical 4x5 doorway. */
    private static final double TRIGGER_SCALE = 1.15;

    /** Half-thickness along normal (axis-aligned slab thickness). */
    private static final double TRIGGER_HALF_THICKNESS = 0.85;

    /** Cooldown between teleports per-player (ticks). */
    private static final int COOLDOWN_TICKS = 40;
    // =======================

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation OPEN = RawAnimation.begin().thenPlayAndHold("open");

    // ===== Per-gate config (set by commands) =====
    private Identifier styleId = GateStyles.ROTTEN; // default style
    private Identifier destWorldId = new Identifier(Oddities.MOD_ID, "rotten_roots"); // default destination
    // ===========================================

    /** Return support (so it isn’t a one-way trap). */
    private static final Map<UUID, ReturnSpot> RETURN = new HashMap<>();
    private record ReturnSpot(RegistryKey<World> dim, Vec3d pos, float yaw, float pitch) {}

    /** Per-player cooldown. */
    private final Object2LongOpenHashMap<UUID> lastTeleportTick = new Object2LongOpenHashMap<>();

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final AnimationController<DimensionalGateBlockEntity> controller =
            new AnimationController<>(this, this::animPredicate);

    public DimensionalGateBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.DIMENSIONAL_GATE_BE, pos, state);

        // Ensure dest defaults match style defaults at construction time
        GateStyle s = GateStyles.get(styleId);
        if (s != null && s.defaultDestWorldId() != null) {
            this.destWorldId = s.defaultDestWorldId();
        }
    }

    // ==========================================================
    // API EXPECTED BY YOUR COMMANDS
    // ==========================================================

    public Identifier getStyleId() {
        return styleId;
    }

    public Identifier getDestWorldId() {
        return destWorldId;
    }

    /** Convenience: resolved style record (never null; falls back). */
    public GateStyle getStyle() {
        return GateStyles.get(styleId);
    }

    /**
     * Set style id for THIS gate instance.
     * @param applyDefaults if true, destWorldId is set to the style default destination (if present).
     */
    public void setStyleId(Identifier id, boolean applyDefaults) {
        if (id == null) return;
        this.styleId = id;

        if (applyDefaults) {
            GateStyle s = GateStyles.get(id);
            if (s != null && s.defaultDestWorldId() != null) {
                this.destWorldId = s.defaultDestWorldId();
            }
        }

        syncToClient();
    }

    public void setDestWorldId(Identifier id) {
        if (id == null) return;
        this.destWorldId = id;
        syncToClient();
    }

    /** KEEP THIS: your block calls it so client FX can key off open/close. */
    public void onOpenStateChanged() {
        // You can later fire a S2C packet here if you want one-shot FX.
        syncToClient();
    }

    // ==========================================================
    // GeckoLib
    // ==========================================================

    private PlayState animPredicate(AnimationState<DimensionalGateBlockEntity> state) {
        if (state.getController().getCurrentAnimation() == null) {
            state.getController().setAnimationSpeed(100);
            delayedSpeedReset(state);
        }
        boolean open = getCachedState().get(DimensionalGateBlock.OPEN);
        return state.setAndContinue(open ? OPEN : IDLE);
    }

    private static void delayedSpeedReset(AnimationState<DimensionalGateBlockEntity> state) {
        new Timer().schedule(new TimerTask() {
            @Override public void run() {
                state.getController().setAnimationSpeed(1);
            }
        }, 750);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(controller);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // ==========================================================
    // Server tick: detect + schedule teleport
    // ==========================================================

    public static void serverTick(World world, BlockPos basePos, BlockState state, DimensionalGateBlockEntity be) {
        if (!(world instanceof ServerWorld sw)) return;
        if (!state.get(DimensionalGateBlock.OPEN)) return;

        Direction facing = state.get(DimensionalGateBlock.FACING);
        Direction rightD = facing.rotateYClockwise();

        // === same center math as your portal shader (controller is bottom-left) ===
        double cx = basePos.getX() + 0.5 + rightD.getOffsetX() * ((DimensionalGateBlock.WIDTH - 1) / 2.0);
        double cy = basePos.getY() + ((DimensionalGateBlock.HEIGHT - 1) / 2.0) + 0.5;
        double cz = basePos.getZ() + 0.5 + rightD.getOffsetZ() * ((DimensionalGateBlock.WIDTH - 1) / 2.0);

        Vec3d normal = new Vec3d(facing.getOffsetX(), 0.0, facing.getOffsetZ()).normalize();

        // Visual plane (matches your FX convention: + along facing)
        Vec3d visualCenter = new Vec3d(cx, cy, cz).add(normal.multiply(VISUAL_PUSH));

        // Trigger center slightly toward player (default -0.10)
        Vec3d triggerCenter = visualCenter.add(normal.multiply(TRIGGER_OFFSET_FROM_VISUAL));

        // doorway half extents (scaled)
        double halfW = (DimensionalGateBlock.WIDTH / 2.0) * TRIGGER_SCALE;
        double halfH = (DimensionalGateBlock.HEIGHT / 2.0) * TRIGGER_SCALE;

        // axis-aligned extents depend on which axis is the normal
        double ex = (Math.abs(normal.x) > 0.5) ? TRIGGER_HALF_THICKNESS : halfW;
        double ez = (Math.abs(normal.z) > 0.5) ? TRIGGER_HALF_THICKNESS : halfW;

        Box portalBox = new Box(
                triggerCenter.x - ex, triggerCenter.y - halfH, triggerCenter.z - ez,
                triggerCenter.x + ex, triggerCenter.y + halfH, triggerCenter.z + ez
        );

        long now = sw.getTime();

        if (DEBUG && (now % 20 == 0)) {
            int c = sw.getEntitiesByClass(ServerPlayerEntity.class, portalBox, p -> true).size();
            Oddities.LOGGER.info("[GateTP] gate={} box={} players={}", basePos, portalBox, c);
        }

        for (ServerPlayerEntity p : sw.getEntitiesByClass(ServerPlayerEntity.class, portalBox, pl -> true)) {
            // Helpful debug: if you’re testing in spectator, you’ll see why it doesn’t TP
            if (p.isSpectator()) {
                if (DEBUG && (now % 20 == 0)) {
                    Oddities.LOGGER.info("[GateTP] player={} is spectator -> skipping TP", p.getName().getString());
                }
                continue;
            }

            UUID id = p.getUuid();
            long last = be.lastTeleportTick.getOrDefault(id, Long.MIN_VALUE);
            if (now - last < COOLDOWN_TICKS) continue;
            be.lastTeleportTick.put(id, now);

            if (DEBUG) {
                Vec3d pp = p.getPos();
                Oddities.LOGGER.info("[GateTP] HIT player={} pos=({}, {}, {}) style={} dest={}",
                        p.getName().getString(),
                        String.format("%.2f", pp.x),
                        String.format("%.2f", pp.y),
                        String.format("%.2f", pp.z),
                        be.styleId,
                        be.destWorldId
                );
            }

            scheduleGateTeleport(sw.getServer(), p.getUuid(), be.getStyleId(), be.getDestWorldId());
        }
    }

    /**
     * Teleport scheduled onto server executor to match “command timing”.
     */
    private static void scheduleGateTeleport(MinecraftServer server, UUID playerId, Identifier styleId, Identifier destWorldId) {
        if (server == null) return;

        server.execute(() -> {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(playerId);
            if (p == null) return;

            if (DEBUG) {
                Oddities.LOGGER.info("[GateTP] EXEC player={} nowDim={} -> dest={}",
                        p.getName().getString(),
                        p.getWorld().getRegistryKey().getValue(),
                        destWorldId
                );
            }

            // Build dest key from Identifier
            RegistryKey<World> destKey = RegistryKey.of(RegistryKeys.WORLD, destWorldId);

            // If already in destination -> return
            if (p.getWorld().getRegistryKey().equals(destKey)) {
                ReturnSpot r = RETURN.remove(p.getUuid());
                if (r != null) {
                    ServerWorld back = server.getWorld(r.dim);
                    if (back != null) {
                        p.teleport(back, r.pos.x, r.pos.y, r.pos.z, r.yaw, r.pitch);
                        if (DEBUG) Oddities.LOGGER.info("[GateTP] DONE return -> {}", back.getRegistryKey().getValue());
                        return;
                    }
                }

                ServerWorld overworld = server.getOverworld();
                BlockPos sp = overworld.getSpawnPos();
                p.teleport(overworld, sp.getX() + 0.5, sp.getY() + 1.0, sp.getZ() + 0.5, p.getYaw(), p.getPitch());
                if (DEBUG) Oddities.LOGGER.info("[GateTP] DONE fallback return -> overworld");
                return;
            }

            // Store return
            RETURN.put(p.getUuid(), new ReturnSpot(p.getWorld().getRegistryKey(), p.getPos(), p.getYaw(), p.getPitch()));

            ServerWorld dst = server.getWorld(destKey);
            if (dst == null) {
                Oddities.LOGGER.error("[GateTP] DST NULL. dest={} loaded={}",
                        destWorldId,
                        server.getWorldRegistryKeys().stream().map(k -> k.getValue().toString()).toList()
                );
                p.sendMessage(Text.literal("GateTP: destination world not loaded: " + destWorldId), true);
                return;
            }

            // Safe spawn-ish coords (same style as your command)
            p.teleport(dst, 0.5, 120.0, 0.5, p.getYaw(), p.getPitch());

            if (DEBUG) {
                Oddities.LOGGER.info("[GateTP] DONE enter -> {} nowDim={}",
                        dst.getRegistryKey().getValue(),
                        p.getWorld().getRegistryKey().getValue()
                );
            }
            p.sendMessage(Text.literal("GateTP: teleported to " + destWorldId), true);
        });
    }

    // ==========================================================
    // NBT + client sync
    // ==========================================================

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        if (styleId != null) nbt.putString("StyleId", styleId.toString());
        if (destWorldId != null) nbt.putString("DestWorldId", destWorldId.toString());
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);

        if (nbt.contains("StyleId")) {
            try { styleId = new Identifier(nbt.getString("StyleId")); } catch (Exception ignored) {}
        }
        if (nbt.contains("DestWorldId")) {
            try { destWorldId = new Identifier(nbt.getString("DestWorldId")); } catch (Exception ignored) {}
        }

        // If dest not present, default to style default
        if (destWorldId == null) {
            GateStyle s = GateStyles.get(styleId);
            if (s != null && s.defaultDestWorldId() != null) destWorldId = s.defaultDestWorldId();
        }
        if (styleId == null) styleId = GateStyles.ROTTEN;
    }

    @Override
    public NbtCompound toInitialChunkDataNbt() {
        NbtCompound nbt = new NbtCompound();
        writeNbt(nbt);
        return nbt;
    }

    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    private void syncToClient() {
        markDirty();
        if (world != null && !world.isClient) {
            BlockState s = getCachedState();
            world.updateListeners(pos, s, s, 3);
        }
    }
}
