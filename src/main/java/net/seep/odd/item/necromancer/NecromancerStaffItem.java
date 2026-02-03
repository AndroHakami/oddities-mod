// src/main/java/net/seep/odd/item/NecromancerStaffItem.java
package net.seep.odd.item.necromancer;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import net.seep.odd.abilities.power.NecromancerPower;
import net.seep.odd.abilities.necromancer.entity.NecroBoltEntity;
import net.seep.odd.entity.ModEntities;

public class NecromancerStaffItem extends SwordItem {

    private static final int BOLT_COOLDOWN_T = 20 * 4; // 4 seconds
    private static final float BOLT_SPEED = 1.55f;

    public NecromancerStaffItem(Settings settings) {
        // stone-sword-ish: ToolMaterials.STONE
        // (vanilla stone sword is SwordItem(STONE, 3, -2.4f, settings))
        super(ToolMaterials.STONE, 3, -2.4f, settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (world.isClient) {
            return TypedActionResult.pass(stack);
        }

        if (!(user instanceof ServerPlayerEntity sp)) {
            return TypedActionResult.pass(stack);
        }

        // only necromancers get the bolt
        if (!NecromancerPower.isNecromancer(sp)) {
            return TypedActionResult.pass(stack);
        }

        // if in summoning mode, RMB is reserved for summoning (no bolt)
        if (NecromancerPower.isSummonModeActive(sp)) {
            return TypedActionResult.pass(stack);
        }

        if (sp.getItemCooldownManager().isCoolingDown(this)) {
            return TypedActionResult.pass(stack);
        }

        NecroBoltEntity bolt = ModEntities.NECRO_BOLT.create(world);
        if (bolt == null) return TypedActionResult.pass(stack);

        Vec3d eye = sp.getEyePos();
        Vec3d dir = sp.getRotationVector().normalize();

        bolt.setOwner(sp);
        bolt.refreshPositionAndAngles(
                eye.x + dir.x * 0.35,
                eye.y - 0.10 + dir.y * 0.35,
                eye.z + dir.z * 0.35,
                sp.getYaw(),
                sp.getPitch()
        );

        bolt.setVelocity(dir.x * BOLT_SPEED, dir.y * BOLT_SPEED, dir.z * BOLT_SPEED);

        world.spawnEntity(bolt);

        sp.getItemCooldownManager().set(this, BOLT_COOLDOWN_T);

        world.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.ENTITY_WITHER_SHOOT, SoundCategory.PLAYERS, 0.65f, 1.35f);

        return TypedActionResult.success(stack);
    }

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!attacker.getWorld().isClient && attacker instanceof ServerPlayerEntity sp) {
            if (NecromancerPower.isNecromancer(sp) && sp.getMainHandStack().getItem() == this) {
                NecromancerPower.setFocus(sp, target);
            }
        }
        return super.postHit(stack, target, attacker);
    }
}
