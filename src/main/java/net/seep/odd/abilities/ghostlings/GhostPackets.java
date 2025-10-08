package net.seep.odd.abilities.ghostlings;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.seep.odd.abilities.ghostlings.entity.GhostlingEntity;
import net.seep.odd.abilities.ghostlings.screen.client.GhostDashboardScreen;
import net.seep.odd.abilities.ghostlings.screen.client.fighter.FighterControlScreen;
import net.seep.odd.abilities.ghostlings.screen.client.courier.CourierConfirmScreen;
import net.seep.odd.abilities.ghostlings.screen.client.courier.CourierTargetScreen;
import net.seep.odd.abilities.ghostlings.screen.courier.CourierPayScreenHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class GhostPackets {
    private GhostPackets() {}
    private static final String MODID = "odd";

    /* ======= PKT IDs ======= */
    // manage/dashboard
    public static final Identifier S2C_OPEN_DASHBOARD    = new Identifier(MODID, "s2c_open_dashboard");

    public static final Identifier C2S_OPEN_MANAGE       = new Identifier(MODID, "c2s_open_manage"); // keep for completeness
    public static final Identifier C2S_SET_WORK_ORIGIN   = new Identifier(MODID, "c2s_set_work_origin");
    public static final Identifier C2S_TOGGLE_STAY_RANGE = new Identifier(MODID, "c2s_toggle_stay_range");
    public static final Identifier C2S_SET_HOME          = new Identifier(MODID, "c2s_set_home");

    // farmer
    public static final Identifier C2S_FARMER_SET_DEPOSIT= new Identifier(MODID, "c2s_farmer_set_deposit");

    // miner
    public static final Identifier C2S_MINER_BEGIN        = new Identifier(MODID, "c2s_miner_begin");        // ghostId, posA, posB
    public static final Identifier C2S_MINER_SET_DEPOSIT  = new Identifier(MODID, "c2s_miner_set_deposit");  // ghostId, chestPos

    // courier 3-step
    public static final Identifier C2S_COURIER_REQUEST_CONFIRM = new Identifier(MODID, "c2s_courier_request_confirm"); // ghostId, pos
    public static final Identifier S2C_COURIER_OPEN_CONFIRM    = new Identifier(MODID, "s2c_courier_open_confirm");    // ghostId, pos, distance, tears
    public static final Identifier C2S_COURIER_OPEN_PAYMENT    = new Identifier(MODID, "c2s_courier_open_payment");    // ghostId
    public static final Identifier C2S_COURIER_PAY_START       = new Identifier(MODID, "c2s_courier_pay_start");       // ghostId

    // fighter control
    public static final Identifier C2S_FIGHTER_OPEN_CTRL   = new Identifier(MODID, "c2s_fighter_open_ctrl"); // ghostId
    public static final Identifier S2C_FIGHTER_OPEN_CTRL   = new Identifier(MODID, "s2c_fighter_open_ctrl"); // ghostId, mode, hasGuard, guardPos
    public static final Identifier C2S_FIGHTER_SET_FOLLOW  = new Identifier(MODID, "c2s_fighter_set_follow"); // ghostId, enable
    public static final Identifier C2S_FIGHTER_SET_GUARD   = new Identifier(MODID, "c2s_fighter_set_guard");  // ghostId, pos
    public static final Identifier C2S_FIGHTER_CLEAR_MODE  = new Identifier(MODID, "c2s_fighter_clear_mode"); // ghostId

    /* ======= server state for pending courier ======= */
    private static final Map<UUID, PendingCourier> PENDING = new HashMap<>();
    private record PendingCourier(BlockPos pos, int tearsNeeded) {}

    /* ===================== SERVER REG ===================== */
    public static void registerC2S() {
        // Primary manage (works if a client chooses to send us a manage request with the entity id)
        ServerPlayNetworking.registerGlobalReceiver(C2S_OPEN_MANAGE, (server, player, handler, buf, response) -> {
            int entityId = buf.readVarInt();
            server.execute(() -> {
                var e = player.getWorld().getEntityById(entityId);
                if (e instanceof GhostlingEntity g && g.isOwner(player.getUuid())) {
                    player.openHandledScreen(g.getManageFactory());
                }
            });
        });

        // Common manage actions
        ServerPlayNetworking.registerGlobalReceiver(C2S_SET_WORK_ORIGIN, (server, player, handler, buf, response) -> {
            int id = buf.readVarInt();
            BlockPos pos = buf.readBlockPos();
            server.execute(() -> {
                var e = player.getWorld().getEntityById(id);
                if (e instanceof GhostlingEntity g && g.isOwner(player.getUuid())) g.setWorkOrigin(pos);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(C2S_TOGGLE_STAY_RANGE, (server, player, handler, buf, response) -> {
            int id = buf.readVarInt();
            server.execute(() -> {
                var e = player.getWorld().getEntityById(id);
                if (e instanceof GhostlingEntity g && g.isOwner(player.getUuid())) g.toggleStayWithinRange();
            });
        });

        // Set Home
        ServerPlayNetworking.registerGlobalReceiver(C2S_SET_HOME, (server, player, handler, buf, response) -> {
            int id = buf.readVarInt();
            BlockPos pos = buf.readBlockPos();
            server.execute(() -> {
                var e = player.getWorld().getEntityById(id);
                if (e instanceof GhostlingEntity g && g.isOwner(player.getUuid())) g.setHome(pos);
            });
        });

        // Farmer: Set deposit chest (validate it's a chest)
        ServerPlayNetworking.registerGlobalReceiver(C2S_FARMER_SET_DEPOSIT, (server, player, handler, buf, response) -> {
            int id = buf.readVarInt();
            BlockPos pos = buf.readBlockPos();
            server.execute(() -> {
                var e = player.getWorld().getEntityById(id);
                if (!(e instanceof GhostlingEntity g) || !g.isOwner(player.getUuid())) return;

                BlockEntity be = player.getWorld().getBlockEntity(pos);
                if (be instanceof ChestBlockEntity) {
                    g.setFarmerDepositChest(pos);
                    player.sendMessage(Text.literal("Farmer deposit chest set to " + pos.toShortString()), true);
                } else {
                    player.sendMessage(Text.literal("That block isn’t a chest."), true);
                }
            });
        });

        // Miner: Set deposit chest
        ServerPlayNetworking.registerGlobalReceiver(C2S_MINER_SET_DEPOSIT, (server, player, handler, buf, response) -> {
            int id = buf.readVarInt();
            BlockPos pos = buf.readBlockPos();
            server.execute(() -> {
                var e = player.getWorld().getEntityById(id);
                if (!(e instanceof GhostlingEntity g) || !g.isOwner(player.getUuid())) return;
                BlockEntity be = player.getWorld().getBlockEntity(pos);
                if (be instanceof ChestBlockEntity) {
                    g.setMinerDepositChest(pos);
                    player.sendMessage(Text.literal("Miner deposit chest set to " + pos.toShortString()), true);
                } else {
                    player.sendMessage(Text.literal("That block isn’t a chest."), true);
                }
            });
        });

        // Miner: Begin area job (snapshot)
        ServerPlayNetworking.registerGlobalReceiver(C2S_MINER_BEGIN, (server, player, handler, buf, response) -> {
            int id = buf.readVarInt();
            BlockPos a = buf.readBlockPos();
            BlockPos b = buf.readBlockPos();
            server.execute(() -> {
                var e = player.getWorld().getEntityById(id);
                if (!(e instanceof GhostlingEntity g) || !g.isOwner(player.getUuid())) return;
                if (!(player.getWorld() instanceof ServerWorld sw)) return;

                int minX = Math.min(a.getX(), b.getX()), maxX = Math.max(a.getX(), b.getX());
                int minY = Math.min(a.getY(), b.getY()), maxY = Math.max(a.getY(), b.getY());
                int minZ = Math.min(a.getZ(), b.getZ()), maxZ = Math.max(a.getZ(), b.getZ());

                java.util.List<GhostlingEntity.BlockSnapshot> list = new java.util.ArrayList<>();
                BlockPos.Mutable m = new BlockPos.Mutable();
                for (int y = minY; y <= maxY; y++)
                    for (int x = minX; x <= maxX; x++)
                        for (int z = minZ; z <= maxZ; z++) {
                            m.set(x,y,z);
                            BlockState st = sw.getBlockState(m);
                            if (!st.isAir()) {
                                list.add(new GhostlingEntity.BlockSnapshot(m.toImmutable(), st));
                            }
                        }

                g.beginMinerJob(list);
                player.sendMessage(Text.literal("Miner: queued " + list.size() + " blocks."), true);
            });
        });

        // ===== courier step 1 -> ask for confirm
        ServerPlayNetworking.registerGlobalReceiver(C2S_COURIER_REQUEST_CONFIRM, (server, player, handler, buf, response) -> {
            int id = buf.readVarInt();
            BlockPos pos = buf.readBlockPos();
            server.execute(() -> {
                var e = player.getWorld().getEntityById(id);
                if (!(e instanceof GhostlingEntity g) || !g.isOwner(player.getUuid())) return;

                double dist = g.getPos().distanceTo(pos.toCenterPos());
                int tears = computeTears(dist);

                PENDING.put(g.getUuid(), new PendingCourier(pos, tears));

                PacketByteBuf out = PacketByteBufs.create();
                out.writeVarInt(id);
                out.writeBlockPos(pos);
                out.writeDouble(dist);
                out.writeVarInt(tears);
                ServerPlayNetworking.send(player, S2C_COURIER_OPEN_CONFIRM, out);
            });
        });

        // step 2 -> open payment (extended screen handler)
        ServerPlayNetworking.registerGlobalReceiver(C2S_COURIER_OPEN_PAYMENT, (server, player, handler, buf, response) -> {
            int id = buf.readVarInt();
            server.execute(() -> {
                var e = player.getWorld().getEntityById(id);
                if (!(e instanceof GhostlingEntity g) || !g.isOwner(player.getUuid())) return;
                PendingCourier pc = PENDING.get(g.getUuid());
                if (pc == null) return;

                player.openHandledScreen(new CourierPayScreenHandler.Factory(id, pc.pos, pc.tearsNeeded));
            });
        });

        // step 3 -> validate payment & launch
        ServerPlayNetworking.registerGlobalReceiver(C2S_COURIER_PAY_START, (server, player, handler, buf, response) -> {
            int id = buf.readVarInt();
            server.execute(() -> {
                var e = player.getWorld().getEntityById(id);
                if (!(e instanceof GhostlingEntity g) || !g.isOwner(player.getUuid())) return;
                PendingCourier pc = PENDING.get(g.getUuid());
                if (pc == null) return;

                if (!(player.currentScreenHandler instanceof CourierPayScreenHandler pay)) return;
                int count = pay.countTears();
                if (count < pc.tearsNeeded) {
                    player.sendMessage(Text.literal("Need " + pc.tearsNeeded + " Ghast Tears."), true);
                    return;
                }
                pay.consumeTears(pc.tearsNeeded);

                g.beginCourierRun(pc.pos);
                PENDING.remove(g.getUuid());
                player.closeHandledScreen();
            });
        });

        /* ===== fighter control ===== */
        ServerPlayNetworking.registerGlobalReceiver(C2S_FIGHTER_OPEN_CTRL, (server, player, handler, buf, response) -> {
            int id = buf.readVarInt();
            server.execute(() -> {
                var e = player.getWorld().getEntityById(id);
                if (!(e instanceof GhostlingEntity g) || !g.isOwner(player.getUuid())) return;

                PacketByteBuf out = PacketByteBufs.create();
                out.writeVarInt(id);
                out.writeEnumConstant(g.getBehavior());
                boolean hasGuard = g.getGuardCenter() != null;
                out.writeBoolean(hasGuard);
                if (hasGuard) out.writeBlockPos(g.getGuardCenter());
                ServerPlayNetworking.send(player, S2C_FIGHTER_OPEN_CTRL, out);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(C2S_FIGHTER_SET_FOLLOW, (server, player, handler, buf, response) -> {
            int id = buf.readVarInt();
            boolean enable = buf.readBoolean();
            server.execute(() -> {
                var e = player.getWorld().getEntityById(id);
                if (e instanceof GhostlingEntity g && g.isOwner(player.getUuid())) {
                    g.setFollowMode(enable);
                    player.sendMessage(Text.literal(enable ? "Following enabled" : "Following disabled"), true);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(C2S_FIGHTER_SET_GUARD, (server, player, handler, buf, response) -> {
            int id = buf.readVarInt();
            BlockPos pos = buf.readBlockPos();
            server.execute(() -> {
                var e = player.getWorld().getEntityById(id);
                if (e instanceof GhostlingEntity g && g.isOwner(player.getUuid())) {
                    g.setGuardCenter(pos);
                    player.sendMessage(Text.literal("Guard area set @ " + pos.toShortString()), true);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(C2S_FIGHTER_CLEAR_MODE, (server, player, handler, buf, response) -> {
            int id = buf.readVarInt();
            server.execute(() -> {
                var e = player.getWorld().getEntityById(id);
                if (e instanceof GhostlingEntity g && g.isOwner(player.getUuid())) {
                    g.setFollowMode(false);
                    g.setGuardCenter(null);
                    player.sendMessage(Text.literal("Mode set to Normal"), true);
                }
            });
        });
    }

    /* ===================== CLIENT REG ===================== */

    // — selection state for “right-click in world” picking —
    private enum SelectMode { NONE, FARMER_DEPOSIT, MINER_DEPOSIT, MINER_AREA_A, MINER_AREA_B, GUARD_CENTER }
    private static SelectMode SELECT_MODE = SelectMode.NONE;
    private static int SELECT_GHOST_ID = -1;
    private static BlockPos MINER_CORNER_A = null;
    private static boolean SELECTION_EVENT_WIRED = false;

    public static void minerBegin(int ghostId, BlockPos a, BlockPos b) {
        PacketByteBuf out = PacketByteBufs.create();
        out.writeVarInt(ghostId);
        out.writeBlockPos(a);
        out.writeBlockPos(b);
        ClientPlayNetworking.send(C2S_MINER_BEGIN, out);
    }

    // Client helper: set miner deposit chest
    public static void minerSetDeposit(int ghostId, BlockPos chestPos) {
        PacketByteBuf out = PacketByteBufs.create();
        out.writeVarInt(ghostId);
        out.writeBlockPos(chestPos);
        ClientPlayNetworking.send(C2S_MINER_SET_DEPOSIT, out);
    }

    // Fighter helpers
    public static void openFighterControl(int ghostId) {
        PacketByteBuf out = PacketByteBufs.create();
        out.writeVarInt(ghostId);
        ClientPlayNetworking.send(C2S_FIGHTER_OPEN_CTRL, out);
    }
    public static void setFollow(int ghostId, boolean enable) {
        PacketByteBuf out = PacketByteBufs.create();
        out.writeVarInt(ghostId);
        out.writeBoolean(enable);
        ClientPlayNetworking.send(C2S_FIGHTER_SET_FOLLOW, out);
    }
    public static void setGuard(int ghostId, BlockPos center) {
        PacketByteBuf out = PacketByteBufs.create();
        out.writeVarInt(ghostId);
        out.writeBlockPos(center);
        ClientPlayNetworking.send(C2S_FIGHTER_SET_GUARD, out);
    }
    public static void clearMode(int ghostId) {
        PacketByteBuf out = PacketByteBufs.create();
        out.writeVarInt(ghostId);
        ClientPlayNetworking.send(C2S_FIGHTER_CLEAR_MODE, out);
    }

    public static void registerS2CClient() {
        // dashboard open
        ClientPlayNetworking.registerGlobalReceiver(S2C_OPEN_DASHBOARD, (client, handler, buf, response) -> {
            int n = buf.readVarInt();
            GhostDashboardScreen.GhostSummary[] arr = new GhostDashboardScreen.GhostSummary[n];
            for (int i = 0; i < n; i++) {
                java.util.UUID uuid = buf.readUuid();
                int entityId = buf.readVarInt();
                String name = buf.readString();
                GhostlingEntity.Job job = buf.readEnumConstant(GhostlingEntity.Job.class);
                BlockPos pos = buf.readBlockPos();
                float hp = buf.readFloat();
                float max = buf.readFloat();
                boolean working = buf.readBoolean();
                String status = buf.readString();
                float progress = buf.readFloat();
                float mood = buf.readFloat();                 // NEW
                String behavior = buf.readString();           // NEW (NORMAL/FOLLOW/GUARD)
                arr[i] = new GhostDashboardScreen.GhostSummary(uuid, entityId, name, job, pos, hp, max, working, status, progress, mood, behavior);
            }
            client.execute(() -> client.setScreen(new GhostDashboardScreen(arr)));
        });

        // courier confirm (step 1 -> 2)
        ClientPlayNetworking.registerGlobalReceiver(S2C_COURIER_OPEN_CONFIRM, (client, handler, buf, response) -> {
            int ghostId = buf.readVarInt();
            BlockPos pos = buf.readBlockPos();
            double dist = buf.readDouble();
            int tears = buf.readVarInt();
            client.execute(() -> client.setScreen(new CourierConfirmScreen(ghostId, pos, dist, tears)));
        });

        // fighter control open
        ClientPlayNetworking.registerGlobalReceiver(S2C_FIGHTER_OPEN_CTRL, (client, handler, buf, response) -> {
            int ghostId = buf.readVarInt();
            GhostlingEntity.BehaviorMode mode = buf.readEnumConstant(GhostlingEntity.BehaviorMode.class);
            boolean hasGuard = buf.readBoolean();
            BlockPos guard = hasGuard ? buf.readBlockPos() : null;
            client.execute(() -> client.setScreen(new FighterControlScreen(ghostId, mode, guard)));
        });

        // Wire the world right-click picker once
        if (!SELECTION_EVENT_WIRED) {
            UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
                if (!world.isClient()) return ActionResult.PASS;
                if (SELECT_MODE == SelectMode.NONE) return ActionResult.PASS;

                BlockPos pos = hit.getBlockPos();

                switch (SELECT_MODE) {
                    case FARMER_DEPOSIT -> {
                        PacketByteBuf out = PacketByteBufs.create();
                        out.writeVarInt(SELECT_GHOST_ID);
                        out.writeBlockPos(pos);
                        ClientPlayNetworking.send(C2S_FARMER_SET_DEPOSIT, out);
                        toastClient("Farmer deposit chest set: " + pos.toShortString());
                        clearSelection();
                        return ActionResult.SUCCESS;
                    }
                    case MINER_DEPOSIT -> {
                        PacketByteBuf out = PacketByteBufs.create();
                        out.writeVarInt(SELECT_GHOST_ID);
                        out.writeBlockPos(pos);
                        ClientPlayNetworking.send(C2S_MINER_SET_DEPOSIT, out);
                        toastClient("Miner deposit chest set: " + pos.toShortString());
                        clearSelection();
                        return ActionResult.SUCCESS;
                    }
                    case MINER_AREA_A -> {
                        MINER_CORNER_A = pos;
                        SELECT_MODE = SelectMode.MINER_AREA_B;
                        toastClient("Corner A set: " + pos.toShortString() + " — now right-click Corner B");
                        return ActionResult.SUCCESS;
                    }
                    case MINER_AREA_B -> {
                        if (MINER_CORNER_A == null) {
                            SELECT_MODE = SelectMode.MINER_AREA_A;
                            toastClient("Please select Corner A again.");
                            return ActionResult.SUCCESS;
                        }
                        PacketByteBuf out = PacketByteBufs.create();
                        out.writeVarInt(SELECT_GHOST_ID);
                        out.writeBlockPos(MINER_CORNER_A);
                        out.writeBlockPos(pos);
                        ClientPlayNetworking.send(C2S_MINER_BEGIN, out);
                        toastClient("Area set: " + MINER_CORNER_A.toShortString() + " → " + pos.toShortString());
                        clearSelection();
                        return ActionResult.SUCCESS;
                    }
                    case GUARD_CENTER -> {
                        setGuard(SELECT_GHOST_ID, pos);
                        toastClient("Guard center set: " + pos.toShortString());
                        clearSelection();
                        return ActionResult.SUCCESS;
                    }
                    default -> {
                        return ActionResult.PASS;
                    }
                }
            });
            SELECTION_EVENT_WIRED = true;
        }
    }

    /* ===================== SERVER PUSH HELPERS ===================== */
    /** Open per-ghost Manage UI from the server (Primary). */
    public static void openManageServer(ServerPlayerEntity player, GhostlingEntity g) {
        player.openHandledScreen(g.getManageFactory());
    }
    /** Build the dashboard server-side and push to client. */
    public static void openDashboardServer(ServerPlayerEntity player) {
        double HUGE = 1_000_000.0;
        List<GhostlingEntity> mine = player.getWorld().getEntitiesByClass(
                GhostlingEntity.class, player.getBoundingBox().expand(HUGE),
                ge -> ge.isOwner(player.getUuid())
        );

        PacketByteBuf out = PacketByteBufs.create();
        out.writeVarInt(mine.size());
        for (GhostlingEntity ge : mine) {
            out.writeUuid(ge.getUuid());
            out.writeVarInt(ge.getId());
            out.writeString(ge.getName().getString());
            out.writeEnumConstant(ge.getJob());
            out.writeBlockPos(ge.getBlockPos());
            out.writeFloat((float) ge.getHealth());
            out.writeFloat(ge.getMaxHealth());
            out.writeBoolean(ge.isWorking());

            String status;
            float progress = 0f;

            if (ge.getMood() <= 0.10f) {
                status = "Depressed";
            } else if (ge.getBehavior() == GhostlingEntity.BehaviorMode.FOLLOW) {
                status = "Following";
            } else if (ge.getBehavior() == GhostlingEntity.BehaviorMode.GUARD) {
                status = "Guarding";
            } else if (ge.getJob() == GhostlingEntity.Job.COURIER) {
                if (ge.isTravelling()) { status = "In transit"; progress = ge.getTravelProgress(); }
                else status = "Idle";
            } else if (ge.getJob() == GhostlingEntity.Job.MINER) {
                status = ge.isWorking() ? "Mining" : "Idle";
                progress = ge.getJobProgress();
            } else if (ge.getJob() == GhostlingEntity.Job.FARMER) {
                status = ge.isWorking() ? "Farming" : "Idle";
            } else if (ge.getJob() == GhostlingEntity.Job.FIGHTER) {
                status = ge.isWorking() ? "Fighting" : "Idle";
            } else {
                status = "Unassigned";
            }

            out.writeString(status);
            out.writeFloat(progress);
            out.writeFloat(ge.getMood());                               // NEW mood for bar
            out.writeString(ge.getBehavior().name());                   // NEW behavior text
        }
        ServerPlayNetworking.send(player, S2C_OPEN_DASHBOARD, out);
    }

    /* ===================== CLIENT HELPERS ===================== */

    public static void openTargetInput(int ghostId) {
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        mc.setScreen(new CourierTargetScreen(ghostId));
    }

    public static void requestConfirm(int ghostId, BlockPos pos) {
        PacketByteBuf out = PacketByteBufs.create();
        out.writeVarInt(ghostId);
        out.writeBlockPos(pos);
        ClientPlayNetworking.send(C2S_COURIER_REQUEST_CONFIRM, out);
    }

    public static void openPayment(int ghostId) {
        PacketByteBuf out = PacketByteBufs.create();
        out.writeVarInt(ghostId);
        ClientPlayNetworking.send(C2S_COURIER_OPEN_PAYMENT, out);
    }

    public static void payAndStart(int ghostId) {
        PacketByteBuf out = PacketByteBufs.create();
        out.writeVarInt(ghostId);
        ClientPlayNetworking.send(C2S_COURIER_PAY_START, out);
    }

    /* === Begin “right-click to pick” flows === */
    public static void beginFarmerDepositPick(int ghostId) {
        SELECT_GHOST_ID = ghostId;
        SELECT_MODE = SelectMode.FARMER_DEPOSIT;
        toastClient("Right-click a chest to set farmer deposit.");
    }
    public static void beginMinerDepositPick(int ghostId) {
        SELECT_GHOST_ID = ghostId;
        SELECT_MODE = SelectMode.MINER_DEPOSIT;
        toastClient("Right-click a chest to set miner deposit.");
    }
    public static void beginMinerAreaPick(int ghostId) {
        SELECT_GHOST_ID = ghostId;
        SELECT_MODE = SelectMode.MINER_AREA_A;
        MINER_CORNER_A = null;
        toastClient("Right-click Corner A, then Corner B (right-click again to confirm).");
    }
    public static void beginGuardPick(int ghostId) {
        SELECT_GHOST_ID = ghostId;
        SELECT_MODE = SelectMode.GUARD_CENTER;
        toastClient("Right-click a block to set guard center.");
    }

    private static void clearSelection() {
        SELECT_MODE = SelectMode.NONE;
        SELECT_GHOST_ID = -1;
        MINER_CORNER_A = null;
    }

    private static void toastClient(String msg) {
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc != null && mc.player != null) mc.player.sendMessage(Text.literal(msg), true);
    }

    /* ===================== utils ===================== */
    private static int computeTears(double dist) {
        // 1 tear per 64 blocks (ceil), min 1
        int t = (int)Math.ceil(dist / 64.0);
        return Math.max(t, 1);
    }
}
