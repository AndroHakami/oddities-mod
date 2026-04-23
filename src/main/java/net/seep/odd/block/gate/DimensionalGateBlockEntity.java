package net.seep.odd.block.gate;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
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
import net.seep.odd.expeditions.Expeditions;
import net.seep.odd.expeditions.atheneum.AtheneumCommands;
import net.seep.odd.expeditions.rottenroots.RottenRootsCommands;

import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class DimensionalGateBlockEntity extends BlockEntity implements GeoBlockEntity {

    private static final boolean DEBUG = true;

    private static final double VISUAL_PUSH = 0.55;
    private static final double TRIGGER_OFFSET_FROM_VISUAL = -0.10;
    private static final double TRIGGER_SCALE = 1.15;
    private static final double TRIGGER_HALF_THICKNESS = 0.85;
    private static final int COOLDOWN_TICKS = 40;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation OPEN = RawAnimation.begin().thenPlayAndHold("open");

    private Identifier styleId = GateStyles.ROTTEN;
    private Identifier destWorldId = Expeditions.ROTTEN_ROOTS_WORLD.getValue();

    private static final Map<UUID, ReturnSpot> RETURN = new HashMap<>();
    private record ReturnSpot(RegistryKey<World> dim, Vec3d pos, float yaw, float pitch) {}

    private final Object2LongOpenHashMap<UUID> lastTeleportTick = new Object2LongOpenHashMap<>();

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final AnimationController<DimensionalGateBlockEntity> controller =
            new AnimationController<>(this, this::animPredicate);

    public DimensionalGateBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.DIMENSIONAL_GATE_BE, pos, state);

        GateStyle s = GateStyles.get(styleId);
        if (s != null && s.defaultDestWorldId() != null) {
            this.destWorldId = s.defaultDestWorldId();
        }
    }

    public Identifier getStyleId() {
        return styleId;
    }

    public Identifier getDestWorldId() {
        return destWorldId;
    }

    public GateStyle getStyle() {
        return GateStyles.get(styleId);
    }

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

    public void onOpenStateChanged() {
        syncToClient();
    }

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
            @Override
            public void run() {
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

    public static void serverTick(World world, BlockPos basePos, BlockState state, DimensionalGateBlockEntity be) {
        if (!(world instanceof ServerWorld sw)) return;
        if (!state.get(DimensionalGateBlock.OPEN)) return;

        Direction facing = state.get(DimensionalGateBlock.FACING);
        Direction rightD = facing.rotateYClockwise();

        double cx = basePos.getX() + 0.5 + rightD.getOffsetX() * ((DimensionalGateBlock.WIDTH - 1) / 2.0);
        double cy = basePos.getY() + ((DimensionalGateBlock.HEIGHT - 1) / 2.0) + 0.5;
        double cz = basePos.getZ() + 0.5 + rightD.getOffsetZ() * ((DimensionalGateBlock.WIDTH - 1) / 2.0);

        Vec3d normal = new Vec3d(facing.getOffsetX(), 0.0, facing.getOffsetZ()).normalize();
        Vec3d visualCenter = new Vec3d(cx, cy, cz).add(normal.multiply(VISUAL_PUSH));
        Vec3d triggerCenter = visualCenter.add(normal.multiply(TRIGGER_OFFSET_FROM_VISUAL));

        double halfW = (DimensionalGateBlock.WIDTH / 2.0) * TRIGGER_SCALE;
        double halfH = (DimensionalGateBlock.HEIGHT / 2.0) * TRIGGER_SCALE;

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
            if (p.isSpectator()) continue;

            UUID id = p.getUuid();
            long last = be.lastTeleportTick.getOrDefault(id, Long.MIN_VALUE);
            if (now - last < COOLDOWN_TICKS) continue;

            if (DEBUG) {
                Vec3d pp = p.getPos();
                Oddities.LOGGER.info("[GateTP] HIT player={} pos=({}, {}, {}) style={} dest={}",
                        p.getName().getString(),
                        String.format(Locale.ROOT, "%.2f", pp.x),
                        String.format(Locale.ROOT, "%.2f", pp.y),
                        String.format(Locale.ROOT, "%.2f", pp.z),
                        be.styleId,
                        be.destWorldId
                );
            }

            boolean teleported = be.tryTeleportDirect(p);
            if (teleported) {
                be.lastTeleportTick.put(id, now);
            }
        }
    }



    private boolean tryTeleportDirect(ServerPlayerEntity p) {
        if (p == null) return false;

        RegistryKey<World> destKey = RegistryKey.of(RegistryKeys.WORLD, destWorldId);

        if (DEBUG) {
            Oddities.LOGGER.info("[GateTP] TRY DIRECT player={} nowDim={} -> dest={}",
                    p.getName().getString(),
                    p.getWorld().getRegistryKey().getValue(),
                    destWorldId
            );
        }

        if (p.getWorld().getRegistryKey().equals(destKey)) {
            ReturnSpot r = RETURN.remove(p.getUuid());
            if (r != null) {
                return teleportDirect(p, r.dim, r.pos, r.yaw, r.pitch,
                        DEBUG ? Text.literal("GateTP: returned") : null);
            }
            return RottenRootsCommands.returnPlayerToOverworld(p);
        }

        RETURN.put(p.getUuid(), new ReturnSpot(
                p.getWorld().getRegistryKey(), p.getPos(), p.getYaw(), p.getPitch()
        ));

        if (destKey.equals(Expeditions.ROTTEN_ROOTS_WORLD)) {
            boolean ok = RottenRootsCommands.teleportPlayerToRottenRoots(p);
            if (ok && DEBUG) {
                p.sendMessage(Text.literal("GateTP: direct helper -> Rotten Roots"), true);
            }
            return ok;
        }

        if (destKey.equals(Expeditions.ATHENEUM_WORLD)) {
            boolean ok = AtheneumCommands.teleportPlayerToAtheneum(p);
            if (ok && DEBUG) {
                p.sendMessage(Text.literal("GateTP: direct helper -> Atheneum"), true);
            }
            return ok;
        }

        return teleportDirect(p, destKey, new Vec3d(0.5, 120.0, 0.5), p.getYaw(), p.getPitch(),
                DEBUG ? Text.literal("GateTP: direct fallback -> " + destWorldId) : null);
    }

    private static boolean teleportDirect(ServerPlayerEntity p, RegistryKey<World> dim,
                                          Vec3d pos, float yaw, float pitch, Text msg) {
        MinecraftServer server = p.getServer();
        if (server == null) return false;

        ServerWorld target = server.getWorld(dim);
        if (target == null) {
            Oddities.LOGGER.error("[GateTP] DIRECT DST NULL. dim={} loaded={}",
                    dim.getValue(),
                    server.getWorldRegistryKeys().stream().map(k -> k.getValue().toString()).toList());
            p.sendMessage(Text.literal("GateTP: destination world not loaded: " + dim.getValue()), true);
            return false;
        }

        p.stopRiding();
        p.setVelocity(Vec3d.ZERO);
        p.fallDistance = 0f;
        p.teleport(target, pos.x, pos.y, pos.z, yaw, pitch);

        if (DEBUG) {
            Oddities.LOGGER.info("[GateTP] AFTER DIRECT player={} nowDim={} pos={}",
                    p.getName().getString(),
                    p.getWorld().getRegistryKey().getValue(),
                    p.getPos());
        }

        if (msg != null) {
            p.sendMessage(msg, true);
        }
        return true;
    }

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
            try {
                styleId = new Identifier(nbt.getString("StyleId"));
            } catch (Exception ignored) {}
        }
        if (nbt.contains("DestWorldId")) {
            try {
                destWorldId = new Identifier(nbt.getString("DestWorldId"));
            } catch (Exception ignored) {}
        }

        if (destWorldId == null) {
            GateStyle s = GateStyles.get(styleId);
            if (s != null && s.defaultDestWorldId() != null) {
                destWorldId = s.defaultDestWorldId();
            }
        }
        if (styleId == null) {
            styleId = GateStyles.ROTTEN;
        }
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
