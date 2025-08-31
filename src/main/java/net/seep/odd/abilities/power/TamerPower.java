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
            case "secondary" -> new Identifier("odd", "textures/gui/abilities/tamer_party.png"); // set this texture
            case "third" -> new Identifier("odd", "textures/gui/abilities/tamer_summon.png"); // set this texture
            default          -> new Identifier("odd", "textures/gui/abilities/ability_default.png");
        };
    }

    /* ===================== PRIMARY: Capture target ===================== */
    @Override
    public void activate(ServerPlayerEntity player) {
        World w = player.getWorld();
        if (!(w instanceof ServerWorld sw)) return;
        if (!PowerAPI.has(player) || !(Powers.get(PowerAPI.get(player)) instanceof TamerPower)) return;

        LivingEntity target = raycastLiving(player, 16.0);
        if (target == null) {
            player.sendMessage(Text.literal("No valid target."), true);
            return;
        }
        if (!isCapturable(target)) {
            player.sendMessage(Text.literal("This one resists capture."), true);
            return;
        }

        TamerState state = TamerState.get(sw);
        var party = state.partyOf(player.getUuid());
        if (party.size() >= TamerState.MAX_PARTY) {
            player.sendMessage(Text.literal("Party is full."), true);
            return;
        }

        var typeId = Registries.ENTITY_TYPE.getId(target.getType());
        PartyMember member = PartyMember.fromCapture(typeId, target);
        state.addMember(player.getUuid(), member);
        state.markDirty();

        target.discard();
        player.sendMessage(Text.literal("Captured " + member.displayName() + " (Lv." + member.level + ")"), true);
    }

    /* ===================== SECONDARY: Open Party Manager ===================== */
    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld sw)) return;
        TamerState state = TamerState.get(sw);
        net.seep.odd.abilities.net.TamerNet.sendOpenParty(player, state.partyOf(player.getUuid()));
    }

    /* ===================== THIRD: Open radial wheel ===================== */
    // Call from your PowerAPI when the user triggers the third slot.
    @Override public void activateThird(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof net.minecraft.server.world.ServerWorld sw)) return;
        var state = net.seep.odd.abilities.tamer.TamerState.get(sw);
        net.seep.odd.abilities.net.TamerNet.sendOpenWheel(player, state.partyOf(player.getUuid()));
    }

    /* ===================== Server Tick hook (pet follow/assist) ===================== */
    public static void serverTick(ServerPlayerEntity p) {
        net.seep.odd.abilities.tamer.TamerSummons.tick(p);
    }

    /* ===================== Helpers ===================== */
    private static boolean isCapturable(LivingEntity e) {
        return !(e instanceof EnderDragonEntity
                || e instanceof WitherEntity
                || e instanceof WardenEntity
                || e instanceof ElderGuardianEntity)
                && e.isAlive() && !e.isSpectator() && !e.isInvulnerable();
    }

    private static LivingEntity raycastLiving(ServerPlayerEntity p, double range) {
        Vec3d eye  = p.getCameraPosVec(1.0f);
        Vec3d look = p.getRotationVec(1.0f);
        Vec3d end  = eye.add(look.multiply(range));
        Box box = p.getBoundingBox().stretch(look.multiply(range)).expand(1.0D);

        EntityHitResult hit = net.minecraft.entity.projectile.ProjectileUtil.raycast(
                p, eye, end, box,
                e -> e instanceof LivingEntity le && isCapturable(le) && e != p,
                range * range
        );
        return hit != null ? (LivingEntity)hit.getEntity() : null;
    }
}
