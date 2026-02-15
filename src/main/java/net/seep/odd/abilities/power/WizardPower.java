// FILE: src/main/java/net/seep/odd/abilities/power/WizardPower.java
package net.seep.odd.abilities.power;

import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.PersistentState;

import net.seep.odd.Oddities;
import net.seep.odd.abilities.PowerAPI;
import net.seep.odd.abilities.wizard.*;
import net.seep.odd.abilities.wizard.entity.CapybaraFamiliarEntity;
import net.seep.odd.item.ModItems;

import java.util.UUID;

public final class WizardPower implements Power {

    @Override public String id() { return "wizard"; }

    @Override
    public boolean hasSlot(String slot) {
        return "primary".equals(slot) || "secondary".equals(slot) || "third".equals(slot);
    }

    @Override public long cooldownTicks() { return 0; }
    @Override public long secondaryCooldownTicks() { return 0; }

    @Override
    public Identifier iconTexture(String slot) {
        return switch (slot) {
            case "primary"   -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/wizard_element.png");
            case "secondary" -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/wizard_combo.png");
            case "third"     -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/wizard_familiar.png");
            default          -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/ability_default.png");
        };
    }

    @Override
    public String slotLongDescription(String slot) {
        return switch (slot) {
            case "primary" -> "Element Selection: choose Fire / Water / Air / Earth.";
            case "secondary" -> "Magic Combo: choose one of 6 combo spells.";
            case "third" -> "Capybara Familiar: send to orbit a target and buff it, or recall.";
            default -> "Wizard";
        };
    }

    @Override
    public String longDescription() {
        return "A staff-wielding caster using four elements + combo magic, powered by mana. "
                + "Swing casts basic element; right-click charges a big cast that auto-releases when ready.";
    }

    /* ---------------- Network IDs ---------------- */

    public static final Identifier S2C_OPEN_ELEMENT_WHEEL = new Identifier(Oddities.MOD_ID, "wizard/open_element_wheel");
    public static final Identifier S2C_OPEN_COMBO_WHEEL   = new Identifier(Oddities.MOD_ID, "wizard/open_combo_wheel");
    public static final Identifier S2C_MANA_SYNC          = new Identifier(Oddities.MOD_ID, "wizard/mana_sync");
    public static final Identifier S2C_ELEMENT_SYNC       = new Identifier(Oddities.MOD_ID, "wizard/element_sync");
    public static final Identifier S2C_SCREEN_SHAKE       = new Identifier(Oddities.MOD_ID, "wizard/screen_shake");

    public static final Identifier C2S_SET_ELEMENT        = new Identifier(Oddities.MOD_ID, "wizard/set_element");
    public static final Identifier C2S_CAST_COMBO_AT = new Identifier(Oddities.MOD_ID, "wizard/cast_combo_at");



    /* ---------------- Mana ---------------- */

    public static final float MANA_MAX = 100f;
    public static float MANA_REGEN_PER_TICK = 0.750f;
    public static int MANA_SYNC_INTERVAL = 5;

    private static void sendManaHud(ServerPlayerEntity p, boolean hasWizard, float mana) {
        var b = PacketByteBufs.create();
        b.writeBoolean(hasWizard);
        b.writeFloat(mana);
        b.writeFloat(MANA_MAX);
        ServerPlayNetworking.send(p, S2C_MANA_SYNC, b);
    }

    private static void sendElementHud(ServerPlayerEntity p, WizardElement e) {
        var b = PacketByteBufs.create();
        b.writeInt(e.id);
        ServerPlayNetworking.send(p, S2C_ELEMENT_SYNC, b);
    }

    public static void sendScreenShake(ServerPlayerEntity p, int ticks, float strength) {
        var b = PacketByteBufs.create();
        b.writeInt(ticks);
        b.writeFloat(strength);
        ServerPlayNetworking.send(p, S2C_SCREEN_SHAKE, b);
    }

    /* ---------------- Power actions ---------------- */

    @Override
    public void activate(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, S2C_OPEN_ELEMENT_WHEEL, PacketByteBufs.create());
    }

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, S2C_OPEN_COMBO_WHEEL, PacketByteBufs.create());
    }

    @Override
    public void activateThird(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld sw)) return;

        long now = sw.getTime();
        UUID id = player.getUuid();

        CapybaraFamiliarEntity fam = WizardCasting.getOrEnsureFamiliar(sw, player);
        if (fam == null) return;

        // keep familiar element synced server-side
        WizardElement e = getElement(player);
        fam.setElement(e.id);

        // If orbiting -> recall and start cooldown
        if (fam.hasOrbitTarget()) {
            fam.clearOrbitTarget();
            LAST_RECALL_TICK.put(id, now);
            player.sendMessage(Text.literal("Capybara recalled."), true);
            return;
        }

        // If NOT orbiting, sending is blocked only if recall cooldown is active
        if (LAST_RECALL_TICK.containsKey(id)) {
            long lastRecall = LAST_RECALL_TICK.getOrDefault(id, 0L);
            long dt = now - lastRecall;
            if (dt >= 0 && dt < RECALL_COOLDOWN_TICKS) {
                long left = RECALL_COOLDOWN_TICKS - dt;
                player.sendMessage(Text.literal("Recall cooldown: " + (left / 20) + "s"), true);
                return;
            }
        }

        LivingEntity target = WizardCasting.raycastLiving(player, 32.0);
        if (target == null) {
            player.sendMessage(Text.literal("No target."), true);
            return;
        }

        fam.setOrbitTarget(target.getUuid());
        player.sendMessage(Text.literal("Capybara sent!"), true);
    }

    /* ---------------- Lifecycle hooks ---------------- */

    public static void onPowerGained(ServerPlayerEntity p) {
        float mana = getMana(p);
        if (mana <= 0f) {
            setMana(p, MANA_MAX);
            mana = MANA_MAX;
        }
        if (getElement(p) == null) setElement(p, WizardElement.FIRE);

        if (p.getWorld() instanceof ServerWorld sw) {
            WizardCasting.getOrEnsureFamiliar(sw, p).setElement(getElement(p).id);
        }

        sendManaHud(p, true, mana);
        sendElementHud(p, getElement(p));
        p.sendMessage(Text.literal("Wizardry awakened ✨"), true);
    }

    public static void onPowerLost(ServerPlayerEntity p) {
        sendManaHud(p, false, 0f);

        UUID id = p.getUuid();
        LAST_SENT_MANA.removeFloat(id);
        LAST_SENT_TICK.removeLong(id);

        if (p.getWorld() instanceof ServerWorld sw) {
            WizardCasting.despawnFamiliar(sw, id);
        }
    }

    /* ---------------- Tick ---------------- */

    private static final Object2FloatOpenHashMap<UUID> LAST_SENT_MANA = new Object2FloatOpenHashMap<>();
    private static final Object2LongOpenHashMap<UUID>  LAST_SENT_TICK = new Object2LongOpenHashMap<>();

    private static final Object2LongOpenHashMap<UUID>  LAST_RECALL_TICK = new Object2LongOpenHashMap<>();
    private static final long RECALL_COOLDOWN_TICKS = 20L * 8L;

    public static void serverTick(ServerPlayerEntity p) {
        if (!(p.getWorld() instanceof ServerWorld sw)) return;

        WizardTempEntities.tickCleanup(sw);

        // ensure familiar exists + element sync
        CapybaraFamiliarEntity fam = WizardCasting.getOrEnsureFamiliar(sw, p);
        if (fam != null) fam.setElement(getElement(p).id);

        // regen mana
        float mana = getMana(p);
        mana = Math.min(MANA_MAX, mana + MANA_REGEN_PER_TICK);
        setMana(p, mana);

        // AUTO-RELEASE big cast when fully charged
        if (p.isUsingItem() && p.getActiveItem().isOf(ModItems.WALKING_STICK)) {
            WizardElement e = getElement(p);
            int needed = WizardCasting.chargeTicksFor(e);
            int used = p.getItemUseTime();
            if (used >= needed) {
                p.stopUsingItem();
                Vec3d at = WizardCasting.raycastPos(p, 32.0);
                WizardCasting.castBigAt(p, at);
            }
        }

        maybeSyncHud(sw, p, mana);
    }

    private static void maybeSyncHud(ServerWorld sw, ServerPlayerEntity p, float mana) {
        UUID id = p.getUuid();
        long now = sw.getTime();

        float last = LAST_SENT_MANA.getOrDefault(id, -9999f);
        long lastT = LAST_SENT_TICK.getOrDefault(id, -9999L);

        boolean time = (now - lastT) >= MANA_SYNC_INTERVAL;
        boolean changed = Math.abs(mana - last) >= 0.10f;

        if (time || changed) {
            LAST_SENT_MANA.put(id, mana);
            LAST_SENT_TICK.put(id, now);
            sendManaHud(p, true, mana);
        }
    }

    /* ---------------- Registration ---------------- */

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                var pow = Powers.get(PowerAPI.get(p));
                if (pow instanceof WizardPower) serverTick(p);
            }
        });

        // element selection
        ServerPlayNetworking.registerGlobalReceiver(C2S_SET_ELEMENT, (server, player, handler, buf, resp) -> {
            int id = buf.readInt();
            server.execute(() -> {
                WizardElement e = WizardElement.fromId(id);
                setElement(player, e);
                sendElementHud(player, e);

                if (player.getWorld() instanceof ServerWorld sw) {
                    CapybaraFamiliarEntity fam = WizardCasting.getOrEnsureFamiliar(sw, player);
                    if (fam != null) fam.setElement(e.id);
                }

                player.sendMessage(Text.literal("Element: " + e.displayName), true);
            });
        });

        // combo casting at a selected location
// combo casting at a selected location
        ServerPlayNetworking.registerGlobalReceiver(C2S_CAST_COMBO_AT, (server, player, handler, buf, resp) -> {
            int comboId = buf.readInt();
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();

            server.execute(() -> {
                // must be wizard
                if (!(Powers.get(PowerAPI.get(player)) instanceof WizardPower)) return;

                Vec3d at = new Vec3d(x, y, z);

                // anti-cheat sanity
                double max = net.seep.odd.abilities.wizard.WizardTargeting.RANGE + 2.0;
                if (player.getPos().squaredDistanceTo(at) > max * max) return;

                WizardCasting.castComboAt(player, WizardCombo.fromId(comboId), at);
            });
        });


        // keep your left-click logic exactly as you already fixed it elsewhere
        // (do NOT re-break melee unless you want swing-only casting)
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> ActionResult.PASS);
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> ActionResult.PASS);

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity p = handler.getPlayer();
            if (p.getWorld() instanceof ServerWorld sw) WizardCasting.despawnFamiliar(sw, p.getUuid());
        });
    }

    /* ---------------- Mana persistence ---------------- */

    public static float getMana(PlayerEntity p) {
        if (!(p.getWorld() instanceof ServerWorld sw)) return 0f;
        return ManaState.of(sw).get(p.getUuid());
    }

    public static void setMana(PlayerEntity p, float v) {
        if (!(p.getWorld() instanceof ServerWorld sw)) return;
        ManaState.of(sw).put(p.getUuid(), Math.max(0f, Math.min(MANA_MAX, v)));
    }

    private static final class ManaState extends PersistentState {
        private final Object2FloatOpenHashMap<UUID> mana = new Object2FloatOpenHashMap<>();
        float get(UUID id) { return mana.getOrDefault(id, MANA_MAX); }
        void put(UUID id, float v) { mana.put(id, v); markDirty(); }

        @Override public NbtCompound writeNbt(NbtCompound nbt) {
            NbtList list = new NbtList();
            for (Object2FloatMap.Entry<UUID> e : mana.object2FloatEntrySet()) {
                NbtCompound c = new NbtCompound();
                c.putUuid("id", e.getKey());
                c.putFloat("m", e.getFloatValue());
                list.add(c);
            }
            nbt.put("list", list);
            return nbt;
        }

        static ManaState fromNbt(NbtCompound nbt) {
            ManaState s = new ManaState();
            NbtList list = nbt.getList("list", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < list.size(); i++) {
                NbtCompound c = list.getCompound(i);
                s.mana.put(c.getUuid("id"), c.getFloat("m"));
            }
            return s;
        }

        static ManaState of(ServerWorld world) {
            return world.getPersistentStateManager().getOrCreate(ManaState::fromNbt, ManaState::new, "odd_wizard_mana");
        }
    }

    /* ---------------- Element persistence ---------------- */

    public static WizardElement getElement(PlayerEntity p) {
        if (!(p.getWorld() instanceof ServerWorld sw)) return WizardElement.FIRE;
        return ElementState.of(sw).get(p.getUuid());
    }

    public static void setElement(PlayerEntity p, WizardElement e) {
        if (!(p.getWorld() instanceof ServerWorld sw)) return;
        ElementState.of(sw).put(p.getUuid(), e);
    }

    private static final class ElementState extends PersistentState {
        private final Object2LongOpenHashMap<UUID> elementId = new Object2LongOpenHashMap<>();

        WizardElement get(UUID id) {
            long v = elementId.getOrDefault(id, (long)WizardElement.FIRE.id);
            return WizardElement.fromId((int)v);
        }

        void put(UUID id, WizardElement e) { elementId.put(id, e.id); markDirty(); }

        @Override public NbtCompound writeNbt(NbtCompound nbt) {
            NbtList list = new NbtList();
            for (var entry : elementId.object2LongEntrySet()) {
                NbtCompound c = new NbtCompound();
                c.putUuid("id", entry.getKey());
                c.putInt("e", (int)entry.getLongValue());
                list.add(c);
            }
            nbt.put("list", list);
            return nbt;
        }

        static ElementState fromNbt(NbtCompound nbt) {
            ElementState s = new ElementState();
            NbtList list = nbt.getList("list", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < list.size(); i++) {
                NbtCompound c = list.getCompound(i);
                s.elementId.put(c.getUuid("id"), c.getInt("e"));
            }
            return s;
        }

        static ElementState of(ServerWorld world) {
            return world.getPersistentStateManager().getOrCreate(ElementState::fromNbt, ElementState::new, "odd_wizard_element");
        }
    }
}
