package net.seep.odd.abilities.power;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.entity.mob.ElderGuardianEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.seep.odd.abilities.PowerAPI;
import net.seep.odd.abilities.tamer.PartyMember;
import net.seep.odd.abilities.tamer.TamerMoves;
import net.seep.odd.abilities.tamer.TamerState;

/** Power: Tamer — capture + party GUI + radial wheel + summon shell. */
public final class TamerPower implements Power {
    @Override public String id() { return "tamer"; }

    @Override public boolean hasSlot(String slot) {
        return "primary".equals(slot) || "secondary".equals(slot) || "third".equals(slot);
    }

    @Override public long cooldownTicks()          { return 0; }
    @Override public long secondaryCooldownTicks() { return 0; }

    @Override
    public Identifier iconTexture(String slot) {
        return switch (slot) {
            case "primary"   -> new Identifier("odd", "textures/gui/abilities/tamer_command.png");
            case "secondary" -> new Identifier("odd", "textures/gui/abilities/tamer_party.png");
            case "third"     -> new Identifier("odd", "textures/gui/abilities/tamer_summon.png");
            default          -> new Identifier("odd", "textures/gui/abilities/ability_default.png");
        };
    }

    @Override
    public String slotTitle(String slot) {
        return switch (slot) {
            case "primary"   -> "Command";
            case "secondary" -> "Party";
            case "third"     -> "Summon";
            default          -> Power.super.slotTitle(slot);
        };
    }

    @Override
    public String slotLongDescription(String slot) {
        return switch (slot) {
            case "primary"   -> "Open the command wheel: Passive / Follow / Recall / Aggressive.";
            case "secondary" -> "Open your party manager.";
            case "third"     -> "Open your summon wheel.";
            default          -> "";
        };
    }

    @Override
    public String longDescription() {
        return "Capture mobs and lead them in battle. Command companions with a quick radial.";
    }

    /* ===================== PRIMARY: Command Wheel ===================== */
    @Override
    public void activate(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld sw)) return;
        if (!PowerAPI.has(player) || !(Powers.get(PowerAPI.get(player)) instanceof TamerPower)) return;

        TamerState st = TamerState.get(sw);
        var a = st.getActive(player.getUuid());
        boolean hasActive = (a != null && sw.getEntity(a.entity) != null);
        int mode = (a != null && a.mode != null) ? a.mode.ordinal() : TamerState.Mode.FOLLOW.ordinal();
        net.seep.odd.abilities.net.TamerNet.sendOpenCommand(player, hasActive, mode);
    }

    /* ===================== SECONDARY: Open Party Manager ===================== */
    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld sw)) return;
        TamerState state = TamerState.get(sw);
        net.seep.odd.abilities.net.TamerNet.sendOpenParty(player, state.partyOf(player.getUuid()));
    }

    /* ===================== THIRD: Open radial Summon ===================== */
    @Override
    public void activateThird(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld sw)) return;
        var state = TamerState.get(sw);
        net.seep.odd.abilities.net.TamerNet.sendOpenWheel(player, state.partyOf(player.getUuid()));
    }

    /* ===================== Server Tick hook (pet follow/assist) ===================== */
    public static void serverTick(ServerPlayerEntity player) {
        net.seep.odd.abilities.tamer.TamerSummons.tick(player);
    }
}
