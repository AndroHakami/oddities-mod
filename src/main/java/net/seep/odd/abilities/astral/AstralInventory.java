package net.seep.odd.abilities.astral;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import io.netty.buffer.Unpooled;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.World;

import java.util.*;

import net.seep.odd.entity.ModEntities;
import net.seep.odd.entity.creepy.CreepyEntity;
import net.seep.odd.abilities.PowerAPI; // used to trigger your normal toggle/exit

/**
 * Astral inventory lock + timer/cooldown + HUD sync.
 * (Creepy/range logic stays in your other code.)
 */
public final class AstralInventory {
    private AstralInventory() {}

    /* ---------- Config ---------- */
    public static final int MAX_ASTRAL_TICKS = 2 * 60 * 20; // 2 minutes
    public static final int COOLDOWN_TICKS   = 60 * 20;     // 60 seconds

    /* ---------- Net channels (S2C) ---------- */
    public static final Identifier HUD_START_ID = new Identifier("odd", "astral_start");
    public static final Identifier HUD_STOP_ID  = new Identifier("odd", "astral_stop");

    /* ---------- State ---------- */
    private static final Set<UUID> ASTRAL = new HashSet<>();
    private static final Map<UUID, Saved> SAVED = new HashMap<>();
    private static final Map<UUID, UUID> CREEPY_BY_PLAYER = new HashMap<>();

    // NEW: anchor, timer & cooldown
    private static final Map<UUID, GlobalPos> ANCHOR_BY_PLAYER = new HashMap<>();
    private static final Map<UUID, Integer>   TICKS_LEFT       = new HashMap<>();
    private static final Map<UUID, Integer>   COOLDOWN_LEFT    = new HashMap<>();

    /** The Ghost Hand item (set once in init). */
    private static Item GHOST_ITEM;

    public static void init(Item ghostHandItem) {
        GHOST_ITEM = Objects.requireNonNull(ghostHandItem, "ghostHandItem");
        ServerTickEvents.END_SERVER_TICK.register(AstralInventory::enforceTick);
    }

    /** Begin astral: save inv, clear, give Ghost Hand to OFFHAND, spawn Creepy, set anchor, start timer. */
    public static void enter(ServerPlayerEntity p) {
        if (GHOST_ITEM == null) throw new IllegalStateException("AstralInventory.init(ModItems.GHOST_HAND) not called!");
        // cooldown handled by PowerAPI secondary cooldown system
if (ASRALContains(p)) {
            enforceFor(p);
            return;
        }

        SAVED.put(p.getUuid(), Saved.capture(p));
        ASTRAL.add(p.getUuid());

        clearAll(p);

        // Put Ghost Hand into offhand and leave main empty
        p.getInventory().offHand.set(0, new ItemStack(GHOST_ITEM));
        p.getInventory().markDirty();
        p.playerScreenHandler.sendContentUpdates();
        p.closeHandledScreen();

        // Anchor = position of the body at enter (dimension-aware)
        GlobalPos anchor = GlobalPos.create(p.getWorld().getRegistryKey(), p.getBlockPos());
        ANCHOR_BY_PLAYER.put(p.getUuid(), anchor);

        // Start timer
        TICKS_LEFT.put(p.getUuid(), MAX_ASTRAL_TICKS);

        // Tell client to show HUD (anchor + duration)
        sendHudStart(p, anchor, MAX_ASTRAL_TICKS);

        // (If you still want the body entity, spawn it here – unchanged from your code.)
        spawnCreepy(p);
    }

    /** End astral: restore inventory exactly, despawn Creepy, stop HUD, start cooldown. */
    public static void exit(ServerPlayerEntity p) {
        ASTRAL.remove(p.getUuid());

        // Stop HUD & clear timer/anchor
        sendHudStop(p);
        ANCHOR_BY_PLAYER.remove(p.getUuid());
        TICKS_LEFT.remove(p.getUuid());

        // Always despawn your body entity (safe if none)
        despawnCreepy(p);

        Saved s = SAVED.remove(p.getUuid());
        if (s != null) s.restore(p);
        p.playerScreenHandler.sendContentUpdates();

        // Cooldown is handled by the power's secondary cooldown logic.
    }

    public static boolean isAstral(ServerPlayerEntity p) {
        return ASRALContains(p);
    }

    private static boolean ASRALContains(ServerPlayerEntity p) {
        return ASTRAL.contains(p.getUuid());
    }

    private static void enforceFor(ServerPlayerEntity p) {
        var inv = p.getInventory();

        // Ensure offhand has exactly 1 Ghost Hand
        ItemStack off = inv.offHand.get(0);
        if (off.isEmpty() || off.getItem() != GHOST_ITEM) {
            int idx = findGhostInMain(inv.main);
            if (idx >= 0) {
                ItemStack s = inv.main.get(idx);
                s.setCount(1);
                inv.offHand.set(0, s);
                inv.main.set(idx, ItemStack.EMPTY);
            } else {
                inv.offHand.set(0, new ItemStack(GHOST_ITEM));
            }
        } else if (off.getCount() != 1) {
            off.setCount(1);
        }

        // Keep all main slots empty so only the offhand ghost can be used
        for (int i = 0; i < inv.main.size(); i++) {
            if (!inv.main.get(i).isEmpty()) inv.main.set(i, ItemStack.EMPTY);
        }

        // No armor
        for (int i = 0; i < inv.armor.size(); i++) {
            if (!inv.armor.get(i).isEmpty()) inv.armor.set(i, ItemStack.EMPTY);
        }

        // Prevent dropping the Ghost Hand: delete fresh nearby drops
        Box bb = new Box(p.getX() - 1, p.getY() - 1, p.getZ() - 1,
                p.getX() + 1, p.getY() + 1, p.getZ() + 1);

        var items = p.getServerWorld().getEntitiesByClass(ItemEntity.class, bb, it ->
                !it.isRemoved() && !it.getStack().isEmpty() && it.getStack().getItem() == GHOST_ITEM && it.age < 10);

        for (ItemEntity it : items) it.discard();

        inv.markDirty();
    }

    /** Server tick: inventory lock + timer & cooldown countdown (no range here). */
    private static void enforceTick(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (!ASRALContains(p)) continue;

            enforceFor(p);

            // Timer: when it hits 0, exit via your normal toggle path
            int left = Math.max(0, TICKS_LEFT.getOrDefault(p.getUuid(), 0) - 1);
            if (left <= 0) {
                forceToggleExit(p);
            } else {
                TICKS_LEFT.put(p.getUuid(), left);
            }
        }
    }

    /* ---------- Creepy management (unchanged; harmless if you don’t use it) ---------- */
    private static void spawnCreepy(ServerPlayerEntity p) {
        despawnCreepy(p);
        ServerWorld sw = p.getServerWorld();
        CreepyEntity c = ModEntities.CREEPY.create(sw);
        if (c == null) return;
        c.refreshPositionAndAngles(p.getX(), p.getY(), p.getZ(), p.getYaw(), 0f);
        sw.spawnEntity(c);
        c.initFor(p);
        CREEPY_BY_PLAYER.put(p.getUuid(), c.getUuid());
    }

    private static void despawnCreepy(ServerPlayerEntity p) {
        UUID eid = CREEPY_BY_PLAYER.remove(p.getUuid());
        if (eid != null) {
            Entity e = p.getServerWorld().getEntity(eid);
            if (e != null) e.discard();
        }
    }


    /** Called by CreepyEntity when the body is damaged/killed. Forces exit via normal toggle path. */
    public static void onCreepyBroken(ServerWorld world, java.util.UUID owner) {
        if (world == null || owner == null) return;
        ServerPlayerEntity p = world.getServer().getPlayerManager().getPlayer(owner);
        if (p == null) return;
        if (!ASRALContains(p)) return;
        // Use the central toggle path so teleport & cleanup stay unified
        forceToggleExit(p);
    }
    /* ---------- Networking: HUD start/stop ---------- */
    private static void sendHudStart(ServerPlayerEntity p, GlobalPos anchor, int maxTicks) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        // Write dimension + blockpos + duration
        buf.writeIdentifier(anchor.getDimension().getValue());
        buf.writeBlockPos(anchor.getPos());
        buf.writeVarInt(maxTicks);
        ServerPlayNetworking.send(p, HUD_START_ID, buf);
    }

    private static void sendHudStop(ServerPlayerEntity p) {
        ServerPlayNetworking.send(p, HUD_STOP_ID, new PacketByteBuf(Unpooled.buffer()));
    }

    /** Use your usual code path to exit (teleport, etc.). */
    private static void forceToggleExit(ServerPlayerEntity p) {
        PowerAPI.activateSecondary(p); // same path as you pressing your toggle key
    }

    /* ---------- Helpers ---------- */
    private static void clearAll(ServerPlayerEntity p) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.main.size(); i++) inv.main.set(i, ItemStack.EMPTY);
        for (int i = 0; i < inv.armor.size(); i++) inv.armor.set(i, ItemStack.EMPTY);
        inv.offHand.set(0, ItemStack.EMPTY);
    }

    private static int findGhostInMain(DefaultedList<ItemStack> main) {
        for (int i = 0; i < main.size(); i++) {
            ItemStack s = main.get(i);
            if (!s.isEmpty() && s.getItem() == GHOST_ITEM) return i;
        }
        return -1;
    }

    /** Snapshot of all slots + selected slot. */
    private static final class Saved {
        final DefaultedList<ItemStack> main   = DefaultedList.ofSize(36, ItemStack.EMPTY);
        final DefaultedList<ItemStack> armor  = DefaultedList.ofSize(4,  ItemStack.EMPTY);
        final DefaultedList<ItemStack> offhand= DefaultedList.ofSize(1,  ItemStack.EMPTY);
        final int selected;

        private Saved(DefaultedList<ItemStack> main,
                      DefaultedList<ItemStack> armor,
                      DefaultedList<ItemStack> offhand,
                      int selected) {
            copyInto(main,   this.main);
            copyInto(armor,  this.armor);
            copyInto(offhand,this.offhand);
            this.selected = selected;
        }

        static Saved capture(ServerPlayerEntity p) {
            var inv = p.getInventory();
            return new Saved(inv.main, inv.armor, inv.offHand, inv.selectedSlot);
        }

        void restore(ServerPlayerEntity p) {
            var inv = p.getInventory();
            copyInto(this.main,    inv.main);
            copyInto(this.armor,   inv.armor);
            copyInto(this.offhand, inv.offHand);
            inv.selectedSlot = this.selected;
            inv.markDirty();
        }

        private static void copyInto(DefaultedList<ItemStack> from, DefaultedList<ItemStack> to) {
            for (int i = 0; i < to.size(); i++) {
                ItemStack src = (i < from.size()) ? from.get(i) : ItemStack.EMPTY;
                to.set(i, src.isEmpty() ? ItemStack.EMPTY : src.copy());
            }
        }
    }
}
