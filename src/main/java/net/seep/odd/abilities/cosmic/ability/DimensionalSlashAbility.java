package net.seep.odd.abilities.cosmic.ability;

import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.*;
import java.util.List;

public final class DimensionalSlashAbility {
    public static final int MAX_CHARGE_TICKS = 30;         // cap if release reports more
    private static final double MIN_RANGE = 6.0;
    private static final double MAX_RANGE = 18.0;
    private static final float BASE_DAMAGE = 9.0f;
    private static final double PATH_HALF_WIDTH = 0.75;

    public void beginCharge(ServerPlayerEntity player) {
        // CPM “charge” anim (safe if missing)
        try {
            Class.forName("net.seep.odd.abilities.overdrive.client.CpmHooks")
                    .getMethod("play", String.class, int.class)
                    .invoke(null, "cosmic_slash_charge", 10);
        } catch (Throwable ignored) {}
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.PLAYERS, 0.4f, 1.75f);
    }

    public void releaseAndSlash(ServerPlayerEntity player, int heldTicks) {
        ServerWorld world = player.getServerWorld();
        int charge = MathHelper.clamp(heldTicks, 1, MAX_CHARGE_TICKS);
        double t = (double)charge / MAX_CHARGE_TICKS;
        double range = MathHelper.lerp(t, MIN_RANGE, MAX_RANGE);

        Vec3d eye = player.getEyePos();
        Vec3d dir = player.getRotationVec(1.0f).normalize();
        Vec3d end = eye.add(dir.multiply(range));

        damageEntitiesAlongPath(world, player, eye, end, PATH_HALF_WIDTH, BASE_DAMAGE);
        spawnPortalRibbon(world, eye, end);

        try {
            Class.forName("net.seep.odd.abilities.overdrive.client.CpmHooks")
                    .getMethod("play", String.class)
                    .invoke(null, "cosmic_slash_release");
        } catch (Throwable ignored) {}
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.8f, 1.2f);

        // Safe blink to just beyond the end point
        Vec3d target = end.add(dir.multiply(1.0));
        BlockPos pos = BlockPos.ofFloored(target);
        // nudge upward if inside solid
        int top = world.getTopY();
        while (!world.isAir(pos) && pos.getY() < top) pos = pos.up();
        player.networkHandler.requestTeleport(target.x, pos.getY(), target.z, player.getYaw(), player.getPitch());
    }

    private void damageEntitiesAlongPath(ServerWorld world, ServerPlayerEntity attacker,
                                         Vec3d start, Vec3d end, double halfWidth, float dmg) {
        Vec3d delta = end.subtract(start);
        double length = delta.length();
        Vec3d step = delta.normalize().multiply(0.5);
        Vec3d p = start;
        DamageSource src = world.getDamageSources().create(DamageTypes.PLAYER_ATTACK, attacker);
        for (double d = 0; d <= length; d += 0.5) {
            Box box = new Box(p.x - halfWidth, p.y - halfWidth, p.z - halfWidth,
                    p.x + halfWidth, p.y + halfWidth, p.z + halfWidth);
            List<Entity> hits = world.getOtherEntities(attacker, box, e -> e.isAttackable() && e.isAlive());
            for (Entity e : hits) e.damage(src, dmg);
            p = p.add(step);
        }
    }

    private void spawnPortalRibbon(ServerWorld world, Vec3d start, Vec3d end) {
        Vec3d delta = end.subtract(start);
        int steps = Math.max(6, (int)(delta.length() * 4));
        Vec3d step = delta.multiply(1.0/steps);
        Vec3d p = start;
        for (int i=0;i<=steps;i++) {
            world.spawnParticles(ParticleTypes.PORTAL, p.x, p.y, p.z, 3, 0.15, 0.15, 0.15, 0.01);
            p = p.add(step);
        }
    }
}
