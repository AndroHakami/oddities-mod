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

    private static final double ASSIST_RADIUS   = 24.0;
    private static final double PROTECT_RADIUS  = 18.0;
    private static final double FOLLOW_NEAR_SQ  = 2.25;
    private static final double FOLLOW_FAR_SQ   = 100.0;

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
        if (!(pet instanceof MobEntity mob)) return;

        // NEW: purge bad targets set by vanilla/other goals
        var tgt = mob.getTarget();
        if (tgt == mob || tgt == owner) { mob.setTarget(null); mob.setAttacking(false); }

        boolean passive = (a.mode == TamerState.Mode.PASSIVE);
        TamerAI.setPassive(mob, passive);

        switch (a.mode) {
            case PASSIVE -> {
                mob.setTarget(null);
                mob.setAttacking(false);
                if (mob instanceof PathAwareEntity pae) {
                    double d2 = mob.squaredDistanceTo(owner);
                    if (d2 > FOLLOW_FAR_SQ) pae.getNavigation().startMovingTo(owner, 1.15);
                    else if (d2 < FOLLOW_NEAR_SQ) pae.getNavigation().stop();
                }
                return;
            }
            case FOLLOW -> {
                LivingEntity prefer = owner.getAttacking();
                if (prefer != null && prefer.isAlive() && prefer != mob && prefer != owner) {
                    if (mob.getTarget() != prefer) {
                        mob.setTarget(prefer);
                        mob.setAttacking(true);
                        if (mob instanceof PathAwareEntity pae) pae.getNavigation().startMovingTo(prefer, 1.25);
                    }
                }
            }
            case AGGRESSIVE -> {
                Box area = owner.getBoundingBox().expand(ASSIST_RADIUS);
                HostileEntity best = null;
                double bestD = Double.MAX_VALUE;
                for (HostileEntity h : sw.getEntitiesByClass(HostileEntity.class, area, HostileEntity::isAlive)) {
                    if (h == mob) continue; // <<< critical: skip self
                    double d = h.squaredDistanceTo(owner);
                    if (d < bestD) { bestD = d; best = h; }
                }
                if (best != null && mob.getTarget() != best) {
                    mob.setTarget(best);
                    mob.setAttacking(true);
                }
            }
        }

        // Protect owner: hostile targeting owner → retarget to pet
        Box protect = owner.getBoundingBox().expand(PROTECT_RADIUS);
        for (HostileEntity hostile : sw.getEntitiesByClass(HostileEntity.class, protect, HostileEntity::isAlive)) {
            if (hostile == mob) continue; // avoid self
            if (hostile.getTarget() == owner) hostile.setTarget(mob);
        }

        // Navigate toward owner when idle/without target
        if (mob.getTarget() == null && mob instanceof PathAwareEntity pae) {
            double d2 = mob.squaredDistanceTo(owner);
            if (d2 > FOLLOW_FAR_SQ) pae.getNavigation().startMovingTo(owner, 1.25);
            else if (d2 < FOLLOW_NEAR_SQ) pae.getNavigation().stop();
        }
    }
}
