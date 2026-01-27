// src/main/java/net/seep/odd/abilities/fairy/CastLogic.java
package net.seep.odd.abilities.fairy;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.PersistentState;
import net.minecraft.world.RaycastContext;
import net.seep.odd.abilities.power.FairyPower;
import net.seep.odd.block.falseflower.FalseFlowerBlockEntity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class CastLogic {
    private CastLogic() {}

    public static void toggleCastForm(ServerPlayerEntity p, boolean enable) {
        if (!(p.getWorld() instanceof ServerWorld sw)) return;
        var st = CastState.of(sw);
        st.set(p.getUuid(), enable);
        p.sendMessage(Text.literal(enable ? "Cast Form: ON" : "Cast Form: OFF"), true);
    }

    public static boolean isCastFormEnabled(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld sw)) return false;
        return CastState.of(sw).isOn(player.getUuid());
    }

    private static final class CastState extends PersistentState {
        private final Set<UUID> on = new HashSet<>();
        boolean isOn(UUID id) { return on.contains(id); }
        void set(UUID id, boolean enable) { if (enable) on.add(id); else on.remove(id); markDirty(); }

        @Override public NbtCompound writeNbt(NbtCompound nbt) {
            var list = new net.minecraft.nbt.NbtList();
            for (UUID id : on) {
                var c = new net.minecraft.nbt.NbtCompound();
                c.putUuid("id", id);
                list.add(c);
            }
            nbt.put("on", list);
            return nbt;
        }

        static CastState fromNbt(NbtCompound nbt) {
            CastState s = new CastState();
            var list = nbt.getList("on", net.minecraft.nbt.NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < list.size(); i++) {
                s.on.add(list.getCompound(i).getUuid("id"));
            }
            return s;
        }

        static CastState of(ServerWorld world) {
            return world.getPersistentStateManager().getOrCreate(CastState::fromNbt, CastState::new, "odd_fairy_cast");
        }
    }

    public static void tryFireRay(ServerPlayerEntity p, FairySpell spell) {
        if (!(p.getWorld() instanceof ServerWorld sw)) return;

        // ✅ unknown combo does NOTHING and costs NOTHING
        if (spell == FairySpell.NONE) {
            p.sendMessage(Text.literal("No spell"), true);
            return;
        }

        float mana = FairyPower.getMana(p);
        if (mana < FairyPower.CAST_COST) {
            p.sendMessage(Text.literal("Not enough mana"), true);
            return;
        }

        Vec3d start = p.getCameraPosVec(1f);
        Vec3d dir   = p.getRotationVector();
        Vec3d end   = start.add(dir.multiply(32.0));

        HitResult hr = p.getWorld().raycast(new RaycastContext(
                start, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, p));

        FalseFlowerBlockEntity target = null;

        if (hr.getType() == HitResult.Type.BLOCK) {
            BlockHitResult bhr = (BlockHitResult) hr;
            BlockEntity be = p.getWorld().getBlockEntity(bhr.getBlockPos());
            if (be instanceof FalseFlowerBlockEntity flower) target = flower;
        }

        if (target == null) {
            target = findFlowerSoft(sw, start, dir, 32.0, 1.25);
        }

        if (target == null) {
            p.sendMessage(Text.literal("No False Flower targeted"), true);
            return;
        }

        if (spell == FairySpell.RECHARGE) {
            // ✅ uses fairy mana to recharge flower
            target.addMana(35f);
        } else {
            target.assignSpell(spell);
        }

        FairyPower.setMana(p, mana - FairyPower.CAST_COST);
    }

    private static FalseFlowerBlockEntity findFlowerSoft(ServerWorld w, Vec3d start, Vec3d dir, double maxRange, double softRadius) {
        FalseFlowerBlockEntity best = null;
        double bestD2 = Double.MAX_VALUE;

        Vec3d d = dir.normalize();
        double step = 0.5;

        for (double t = 0; t <= maxRange; t += step) {
            Vec3d pt = start.add(d.multiply(t));
            BlockPos base = BlockPos.ofFloored(pt);

            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                BlockPos bp = base.add(dx, dy, dz);
                BlockEntity be = w.getBlockEntity(bp);
                if (!(be instanceof FalseFlowerBlockEntity flower)) continue;

                Vec3d c = Vec3d.ofCenter(bp);
                double d2 = c.squaredDistanceTo(pt);

                if (d2 <= softRadius * softRadius && d2 < bestD2) {
                    best = flower;
                    bestD2 = d2;
                }
            }

            if (best != null && bestD2 < 0.15) break;
        }

        return best;
    }
}
