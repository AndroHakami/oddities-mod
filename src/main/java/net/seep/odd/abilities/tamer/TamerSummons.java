package net.seep.odd.abilities.tamer;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;

public final class TamerSummons {
    private TamerSummons() {}

    // Tunables
    private static final double ASSIST_RADIUS   = 24.0;  // how far around the owner we look for hostiles
    private static final double PROTECT_RADIUS  = 18.0;  // how far we redirect mobs that target the owner
    private static final double FOLLOW_FAR_SQ   = 36.0;  // >6 blocks => move closer
    private static final double FOLLOW_NEAR_SQ  = 6.25;  // <2.5 blocks => stop

    /** Call this once per server tick for the owner (e.g., from TamerPower.serverTick). */
    public static void tick(ServerPlayerEntity owner) {
        if (!(owner.getWorld() instanceof ServerWorld sw)) return;

        TamerState st = TamerState.get(sw);
        var a = st.getActive(owner.getUuid());
        if (a == null) return;

        var e = sw.getEntity(a.entity);
        if (!(e instanceof LivingEntity pet) || !pet.isAlive()) {
            st.clearActive(owner.getUuid());
            st.markDirty();
            return;
        }

        if (pet instanceof MobEntity mob) {
            // 1) Redirect mobs that are targeting the owner to the pet ("taunt")
            Box protect = owner.getBoundingBox().expand(PROTECT_RADIUS);
            for (HostileEntity hostile : sw.getEntitiesByClass(HostileEntity.class, protect, h -> h.isAlive())) {
                if (hostile.getTarget() == owner) {
                    hostile.setTarget(mob);
                }
            }

            // 2) Acquire a target for the pet if it doesn't have one or its current target died
            boolean needsTarget = mob.getTarget() == null || !mob.getTarget().isAlive();
            if (needsTarget) {
                HostileEntity nearest = null;
                double best = Double.MAX_VALUE;

                Box search = owner.getBoundingBox().expand(ASSIST_RADIUS);
                for (HostileEntity hostile : sw.getEntitiesByClass(HostileEntity.class, search, h -> h.isAlive())) {
                    // Prefer hostiles already aggro'd on the owner; otherwise pick the closest to owner
                    double score = (hostile.getTarget() == owner) ? 0.0 : hostile.squaredDistanceTo(owner);
                    if (score < best) { best = score; nearest = hostile; }
                }

                if (nearest != null) {
                    mob.setTarget(nearest);
                }
            }

            // 3) Simple follow when not in combat
            if (mob.getTarget() == null) {
                double d2 = mob.squaredDistanceTo(owner);
                if (d2 > FOLLOW_FAR_SQ) {
                    if (mob instanceof PathAwareEntity pae) {
                        pae.getNavigation().startMovingTo(owner, 1.25);
                    }
                } else if (d2 < FOLLOW_NEAR_SQ) {
                    if (mob instanceof PathAwareEntity pae) {
                        pae.getNavigation().stop();
                    }
                }
            }
        }
    }
}
