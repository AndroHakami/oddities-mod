// FILE: src/main/java/net/seep/odd/abilities/artificer/mixer/brew/AmplifiedJudgementEffect.java
package net.seep.odd.abilities.artificer.mixer.brew;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public final class AmplifiedJudgementEffect {
    private AmplifiedJudgementEffect() {}

    public static final int CHARGE_TICKS = 20 * 2;     // 2s charge
    public static final int BEAM_TICKS   = 20 * 2;     // 2s beam “active”

    public static final float RADIUS      = 10.0f;     // beam radius (blocks)
    public static final float BEAM_HEIGHT = 80.0f;     // should match shader height feel

    // "Ticking" values (vanilla i-frames still apply to damage; healing always applies)
    public static final float DAMAGE_PER_TICK = 6.0f;  // only if they have ANY beneficial effects
    public static final float HEAL_PER_TICK   = 2.0f;  // 1 heart per tick for others

    public static void apply(World world, BlockPos pos, @Nullable LivingEntity thrower, ItemStack stack) {
        if (!(world instanceof ServerWorld sw)) return;

        Vec3d center = Vec3d.ofCenter(pos);
        long id = sw.getRandom().nextLong();

        // faint “charge” visual
        net.seep.odd.abilities.artificer.mixer.AmplifiedJudgementNet.send(sw, id, center, 0, CHARGE_TICKS);

        // schedule beam fire at end of charge
        net.seep.odd.util.TickScheduler.runLater(sw, CHARGE_TICKS, () -> {
            // strong beam visual + sound
            net.seep.odd.abilities.artificer.mixer.AmplifiedJudgementNet.send(sw, id, center, 1, BEAM_TICKS);
            sw.playSound(null, pos, net.seep.odd.sound.ModSounds.HOLY_RAY, SoundCategory.BLOCKS, 1.0f, 1.0f);

            // ticking beam logic for BEAM_TICKS
            for (int i = 0; i < BEAM_TICKS; i++) {
                final int delay = i;
                net.seep.odd.util.TickScheduler.runLater(sw, delay, () -> beamTick(sw, center));
            }
        });
    }

    private static void beamTick(ServerWorld sw, Vec3d center) {
        double r = RADIUS;
        double r2 = r * r;

        // vertical volume of the beam
        Box box = new Box(
                center.x - r, center.y,               center.z - r,
                center.x + r, center.y + BEAM_HEIGHT, center.z + r
        );

        for (LivingEntity t : sw.getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive())) {
            // skip spectators only
            if (t instanceof PlayerEntity p && p.isSpectator()) continue;

            // true cylinder check on XZ
            double dx = t.getX() - center.x;
            double dz = t.getZ() - center.z;
            if ((dx * dx + dz * dz) > r2) continue;

            boolean hasBeneficial = hasAnyBeneficialEffect(t);

            if (hasBeneficial) {
                // ✅ only damage entities/players with ANY positive status effect
                t.damage(sw.getDamageSources().magic(), DAMAGE_PER_TICK);
            } else {
                // ✅ otherwise heal them
                // (heal() won't exceed max health; works for players & mobs)
                t.heal(HEAL_PER_TICK);
            }
        }
    }

    private static boolean hasAnyBeneficialEffect(LivingEntity e) {
        for (StatusEffectInstance inst : e.getStatusEffects()) {
            if (inst.getEffectType().getCategory() == StatusEffectCategory.BENEFICIAL) {
                return true;
            }
        }
        return false;
    }
}