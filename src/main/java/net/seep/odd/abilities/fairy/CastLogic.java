package net.seep.odd.abilities.fairy;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.PersistentState;
import net.minecraft.world.RaycastContext;
import net.seep.odd.abilities.power.FairyPower;
import net.seep.odd.block.falseflower.FalseFlowerBlockEntity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class CastLogic {
    private CastLogic() {}

    /* ================= Cast Form toggle ================= */

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

    /** Per-world persistence for who has Cast Form enabled. */
    private static final class CastState extends PersistentState {
        private final Set<UUID> on = new HashSet<>();

        boolean isOn(UUID id) { return on.contains(id); }
        void set(UUID id, boolean enable) { if (enable) on.add(id); else on.remove(id); markDirty(); }

        @Override public NbtCompound writeNbt(NbtCompound nbt) {
            NbtList list = new NbtList();
            for (UUID id : on) {
                NbtCompound c = new NbtCompound();
                c.putUuid("id", id);
                list.add(c);
            }
            nbt.put("on", list);
            return nbt;
        }

        static CastState fromNbt(NbtCompound nbt) {
            CastState s = new CastState();
            NbtList list = nbt.getList("on", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < list.size(); i++) {
                s.on.add(list.getCompound(i).getUuid("id"));
            }
            return s;
        }

        static CastState of(ServerWorld world) {
            return world.getPersistentStateManager().getOrCreate(CastState::fromNbt, CastState::new, "odd_fairy_cast");
        }
    }

    /* ================= Spell ray ================= */

    /** Fires the selection ray and applies the spell if a False Flower is hit. */
    public static void tryFireRay(ServerPlayerEntity p, FairySpell spell) {
        Vec3d start = p.getCameraPosVec(1f);
        Vec3d dir   = p.getRotationVector();
        Vec3d end   = start.add(dir.multiply(32.0));

        HitResult hr = p.getWorld().raycast(new RaycastContext(
                start, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, p));

        if (hr.getType() == HitResult.Type.BLOCK) {
            BlockHitResult bhr = (BlockHitResult) hr;
            BlockEntity be = p.getWorld().getBlockEntity(bhr.getBlockPos());
            if (be instanceof FalseFlowerBlockEntity flower) {
                if (spell == FairySpell.RECHARGE) {
                    flower.addMana(35f);
                } else {
                    flower.assignSpell(spell);
                }
                // small mana cost to cast
                FairyPower.setMana(p, Math.max(0f, FairyPower.getMana(p) - 5f));
            }
        }
    }
}
