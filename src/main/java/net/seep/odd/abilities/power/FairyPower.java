package net.seep.odd.abilities.power;

import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.PersistentState;
import net.seep.odd.Oddities;
import net.seep.odd.abilities.fairy.CastLogic;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Fairy power:
 * - Player is small (Pehkui 0.5), has 6 hearts.
 * - Creative-like flight; drains mana while airborne & flying; slow passive mana regen (berries boost).
 * - Primary: toggle Cast Form (↑ ↓ ← → combo → spell ray → False Flower).
 * - Secondary: open Manage Flowers menu.
 *
 * Matches your Power interface like IceWitchPower.
 */
public final class FairyPower implements Power {
    /* ---------------- Power metadata ---------------- */

    @Override public String id() { return "fairy"; }

    @Override public boolean hasSlot(String slot) {
        return "primary".equals(slot) || "secondary".equals(slot);
    }

    @Override public long cooldownTicks() { return 0; }

    @Override public long secondaryCooldownTicks() { return 0; }

    @Override
    public Identifier iconTexture(String slot) {
        return switch (slot) {
            case "primary"   -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/fairy_cast.png");
            case "secondary" -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/fairy_manage.png");
            default          -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/ability_default.png");
        };
    }

    @Override
    public String slotLongDescription(String slot) {
        return switch (slot) {
            case "primary" ->
                    "Cast Form: enter 3-arrow input (↑ ↓ ← →) to compose a spell. Fires a light ray; "
                            + "hitting a False Flower assigns/charges its magic.";
            case "secondary" ->
                    "Manage Flowers: open a list of your active False Flowers; toggle, rename, and set power.";
            default -> "Fairy";
        };
    }

    @Override
    public String longDescription() {
        return "A tiny caster with creative-style flight that consumes mana. Place False Flowers and imbue them "
                + "with spells using Cast Form combos.";
    }

    /* ---------------- Network (same IDs as before) ---------------- */

    public static final Identifier C2S_CAST_COMMIT = new Identifier(Oddities.MOD_ID, "fairy/cast_commit");
    public static final Identifier C2S_TOGGLE_CAST = new Identifier(Oddities.MOD_ID, "fairy/toggle_cast");
    public static final Identifier C2S_OPEN_MENU   = new Identifier(Oddities.MOD_ID, "fairy/open_menu");

    /* ---------------- Mana + flight tuning ---------------- */

    public static final float MANA_MAX = 100f;
    private static final float MANA_REGEN_PER_TICK = 0.015f;
    private static final float FLIGHT_DRAIN_PER_TICK = 0.30f;

    private static final UUID HP_MOD_ID = UUID.fromString("7a2d89d0-3c80-4caa-82d6-6e2e1f8d66c1");

    /* ---------------- Power actions ---------------- */

    /** Primary: toggle Cast Form (also usable from your key handler). */
    @Override
    public void activate(ServerPlayerEntity player) {
        // If you also toggle from the keybinding, this keeps things in sync.
        // We just flip whatever the client sent last (CastLogic persists a small flag on the server).
        CastLogic.toggleCastForm(player, !CastLogic.isCastFormEnabled(player));
    }

    /** Secondary: open flower manager UI. */
    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        net.seep.odd.abilities.fairy.FlowerMenu.openFor(player);
    }

    /* ---------------- Lifecycle hooks (call these from your power manager) ---------------- */

    public static void onPowerGained(ServerPlayerEntity p) {
        applyHalfHearts(p);
        applyPehkuiScale(p, 0.5f);
        enableFlight(p, true);
        if (getMana(p) <= 0f) setMana(p, MANA_MAX);
        p.sendMessage(Text.literal("Fairy form awakened ✨"), true);
    }

    public static void onPowerLost(ServerPlayerEntity p) {
        clearHalfHearts(p);
        applyPehkuiScale(p, 1.0f);
        enableFlight(p, false);
    }

    /* ---------------- Tick (per-player) ---------------- */

    /** Call once per tick for players that currently have the Fairy power. */
    public static void serverTick(ServerPlayerEntity p) {
        if (!(p.getWorld() instanceof ServerWorld sw)) return;

        float mana = getMana(p);
        boolean flying = p.getAbilities().flying;

        // Drain while flying & airborne; otherwise regen
        if (flying && !p.isOnGround()) {
            mana = Math.max(0f, mana - FLIGHT_DRAIN_PER_TICK);
            if (mana == 0f) {
                p.getAbilities().flying = false;
                p.sendAbilitiesUpdate();
            }
        } else {
            mana = Math.min(MANA_MAX, mana + MANA_REGEN_PER_TICK);
        }

        // Berries top-up
        if (p.getActiveItem().isOf(Items.SWEET_BERRIES) || p.getActiveItem().isOf(Items.GLOW_BERRIES)) {
            mana = Math.min(MANA_MAX, mana + 0.8f);
        }

        setMana(p, mana);
    }

    /* ---------------- Packet registration (wire once from common init) ---------------- */

    public static void register() {
        // Optionally keep a global tick here to regen everyone automatically too:
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                // Only adjust mana flight if they actually have the power
                // (replace the check with your real ownership logic if needed)
                if (p.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH) != null) {
                    serverTick(p);
                }
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(C2S_CAST_COMMIT, (server, player, handler, buf, resp) -> {
            byte a0 = buf.readByte(), a1 = buf.readByte(), a2 = buf.readByte();
            var spell = net.seep.odd.abilities.fairy.FairySpell.fromInputs(a0, a1, a2);
            server.execute(() -> CastLogic.tryFireRay(player, spell));
        });
        ServerPlayNetworking.registerGlobalReceiver(C2S_TOGGLE_CAST, (server, player, handler, buf, resp) ->
                server.execute(() -> CastLogic.toggleCastForm(player, buf.readBoolean())));
        ServerPlayNetworking.registerGlobalReceiver(C2S_OPEN_MENU, (server, player, handler, buf, resp) ->
                server.execute(() -> net.seep.odd.abilities.fairy.FlowerMenu.openFor(player)));
    }

    /* ---------------- Mana persistence (Fabric friendly) ---------------- */

    public static float getMana(PlayerEntity p) {
        if (!(p.getWorld() instanceof ServerWorld sw)) return 0f;
        return ManaState.of(sw).get(p.getUuid());
    }

    public static void setMana(PlayerEntity p, float v) {
        if (!(p.getWorld() instanceof ServerWorld sw)) return;
        ManaState.of(sw).put(p.getUuid(), Math.max(0f, Math.min(MANA_MAX, v)));
    }

    /** Saves per-player mana in the world save. */
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
            return world.getPersistentStateManager().getOrCreate(ManaState::fromNbt, ManaState::new, "odd_fairy_mana");
        }
    }

    /* ---------------- HP & Pehkui helpers ---------------- */

    private static void enableFlight(ServerPlayerEntity p, boolean on) {
        p.getAbilities().allowFlying = on;
        if (!on) p.getAbilities().flying = false;
        p.sendAbilitiesUpdate();
    }

    private static void applyHalfHearts(ServerPlayerEntity p) {
        var inst = p.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (inst == null) return;
        inst.removeModifier(HP_MOD_ID);
        double target = 12.0; // 6 hearts
        double delta = target - inst.getBaseValue();
        inst.addPersistentModifier(new EntityAttributeModifier(HP_MOD_ID, "FairyHP", delta, EntityAttributeModifier.Operation.ADDITION));
        if (p.getHealth() > 12f) p.setHealth(12f);
    }

    private static void clearHalfHearts(ServerPlayerEntity p) {
        var inst = p.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (inst != null) inst.removeModifier(HP_MOD_ID);
    }

    private static final BiConsumer<net.minecraft.entity.Entity, Float> PEHKUI_SCALE_SET = findPehkuiSetter();

    private static BiConsumer<net.minecraft.entity.Entity, Float> findPehkuiSetter() {
        try {
            Class<?> scaleTypesCl = Class.forName("virtuoel.pehkui.api.ScaleTypes");
            Object BASE = scaleTypesCl.getField("BASE").get(null);

            Class<?> scaleTypeCl = Class.forName("virtuoel.pehkui.api.ScaleType");
            Method getScaleData = scaleTypeCl.getMethod("getScaleData", net.minecraft.entity.Entity.class);

            Class<?> scaleDataCl = Class.forName("virtuoel.pehkui.api.ScaleData");
            Method setScale = scaleDataCl.getMethod("setScale", float.class);

            return (entity, scale) -> {
                try {
                    Object data = getScaleData.invoke(BASE, entity);
                    setScale.invoke(data, scale);
                } catch (Throwable ignored) {}
            };
        } catch (Throwable t) {
            return (e, s) -> {};
        }
    }

    public static void applyPehkuiScale(PlayerEntity p, float s) {
        try { PEHKUI_SCALE_SET.accept(p, s); } catch (Throwable ignored) {}
    }
}
