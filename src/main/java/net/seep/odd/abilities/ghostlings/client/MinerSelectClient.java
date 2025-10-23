package net.seep.odd.abilities.ghostlings.client;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.seep.odd.abilities.ghostlings.GhostPackets;

/**
 * Client-only helper that lets you pick a miner area & deposit chest by
 * right-clicking in the world, while showing a live box outline + size.
 */
public final class MinerSelectClient {

    private MinerSelectClient() {}

    // ---- selection state ----
    private static boolean wired = false;

    private static boolean selecting = false;   // picking area right now
    private static boolean depositMode = false; // picking chest right now
    private static int currentGhostId = -1;

    private static BlockPos cornerA = null;
    private static BlockPos cornerB = null;

    // ---- public API ----

    /** Begin "pick area" flow: Right-click A, right-click B, right-click again to confirm. Sneak+Right-click cancels. */
    public static void beginAreaPick(int ghostEntityId) {
        ensureInit();
        currentGhostId = ghostEntityId;
        selecting = true;
        depositMode = false;
        cornerA = null;
        cornerB = null;
        toast("Miner: right-click Corner A, then Corner B. Sneak+Right-click to cancel.");
    }

    /** Begin "pick deposit chest" flow: right-click the chest once. */
    public static void beginDepositPick(int ghostEntityId) {
        ensureInit();
        currentGhostId = ghostEntityId;
        selecting = false;
        depositMode = true;
        cornerA = null;
        cornerB = null;
        toast("Miner: right-click a chest to set as deposit. Sneak+Right-click to cancel.");
    }

    // ---- wiring ----

    private static void ensureInit() {
        if (wired) return;
        wired = true;

        // 1) Intercept right-clicks on blocks (client side) for both flows
        UseBlockCallback.EVENT.register((PlayerEntity player, World world, Hand hand, BlockHitResult hit) -> {
            if (!world.isClient()) return ActionResult.PASS;

            // cancel flows with sneak+right-click
            if ((selecting || depositMode) && player.isSneaking()) {
                clear();
                toast("Selection cancelled.");
                return ActionResult.SUCCESS;
            }

            if (depositMode) {
                // Single-click sets the chest; don't let it open
                BlockPos pos = hit.getBlockPos();
                GhostPackets.Client.minerSetDeposit(currentGhostId, pos);
                toast("Miner deposit chest set: " + pos.toShortString());
                clear();
                return ActionResult.SUCCESS;
            }

            if (!selecting) return ActionResult.PASS;

            // Area pick – A -> B -> confirm
            BlockPos pos = hit.getBlockPos();
            if (cornerA == null) {
                cornerA = pos.toImmutable();
                toast("Miner: first corner " + cornerA.toShortString());
                return ActionResult.SUCCESS; // consume so blocks don't open
            } else if (cornerB == null) {
                cornerB = pos.toImmutable();
                var dims = dimsText(cornerA, cornerB);
                toast("Miner: second corner " + cornerB.toShortString() +
                        " — " + dims + ". Right-click again to CONFIRM, Sneak+Right-click to CANCEL.");
                return ActionResult.SUCCESS;
            } else {
                // confirm
                GhostPackets.Client.minerBegin(currentGhostId, cornerA, cornerB);
                toast("Miner: area confirmed. Beginning work.");
                clear();
                return ActionResult.SUCCESS;
            }
        });

        // 2) Draw a cyan outline + live dimension/volume while selecting
        WorldRenderEvents.AFTER_ENTITIES.register(ctx -> {
            if (!selecting && !depositMode) return;

            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.world == null) return;

            MatrixStack matrices = ctx.matrixStack();
            Vec3d cam = ctx.camera().getPos();

            // Which "other" corner do we show? If B isn't set yet, use the
            // current crosshair block as the moving corner.
            BlockPos a = cornerA;
            BlockPos b = cornerB;

            if (selecting && a != null && b == null) {
                HitResult hr = mc.crosshairTarget;
                if (hr instanceof BlockHitResult bhr) {
                    b = bhr.getBlockPos();
                } else {
                    b = a;
                }
            }

            // When picking deposit we still show the hovered block (thin box),
            // so users get feedback about what they’ll click.
            if (depositMode) {
                HitResult hr = mc.crosshairTarget;
                if (hr instanceof BlockHitResult bhr) {
                    BlockPos p = bhr.getBlockPos();
                    drawBox(matrices, cam, new Box(p));
                }
                return;
            }

            if (a == null || b == null) return;

            // Inclusive block box (expand by +1 for max bounds for edges to hug block faces)
            int minX = Math.min(a.getX(), b.getX());
            int minY = Math.min(a.getY(), b.getY());
            int minZ = Math.min(a.getZ(), b.getZ());
            int maxX = Math.max(a.getX(), b.getX()) + 1;
            int maxY = Math.max(a.getY(), b.getY()) + 1;
            int maxZ = Math.max(a.getZ(), b.getZ()) + 1;
            drawBox(matrices, cam, new Box(minX, minY, minZ, maxX, maxY, maxZ));

            // On-screen overlay with size info
            mc.inGameHud.setOverlayMessage(Text.literal(dimsText(a, b)), false);
        });
    }

    // ---- helpers ----

    private static void clear() {
        selecting = false;
        depositMode = false;
        cornerA = null;
        cornerB = null;
        currentGhostId = -1;

        var mc = MinecraftClient.getInstance();
        if (mc != null && mc.inGameHud != null) {
            mc.inGameHud.setOverlayMessage(null, false);
        }
    }

    private static void toast(String s) {
        var mc = MinecraftClient.getInstance();
        if (mc != null && mc.player != null) {
            mc.player.sendMessage(Text.literal(s), true);
        }
    }

    private static String dimsText(BlockPos a, BlockPos b) {
        int dx = Math.abs(a.getX() - b.getX()) + 1;
        int dy = Math.abs(a.getY() - b.getY()) + 1;
        int dz = Math.abs(a.getZ() - b.getZ()) + 1;
        long vol = (long) dx * dy * dz;
        return dx + "×" + dy + "×" + dz + " (" + vol + " blocks)";
    }

    private static void drawBox(MatrixStack matrices, Vec3d cam, Box box) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;

        // Slightly expand so lines don't z-fight with block faces
        Box expanded = box.expand(0.002);

        VertexConsumerProvider.Immediate vcp = mc.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer lines = vcp.getBuffer(RenderLayer.getLines());

        // Translate by camera
        double minX = expanded.minX - cam.x;
        double minY = expanded.minY - cam.y;
        double minZ = expanded.minZ - cam.z;
        double maxX = expanded.maxX - cam.x;
        double maxY = expanded.maxY - cam.y;
        double maxZ = expanded.maxZ - cam.z;

        // cyan outline (r,g,b,a)
        WorldRenderer.drawBox(matrices, lines, minX, minY, minZ, maxX, maxY, maxZ,
                0f, 1f, 1f, 1f);

        vcp.draw();
    }
}
