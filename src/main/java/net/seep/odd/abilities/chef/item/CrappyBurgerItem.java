// src/main/java/net/seep/odd/abilities/chef/item/CrappyBurgerItem.java
package net.seep.odd.abilities.chef.item;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import net.seep.odd.item.custom.TooltipItem;

public class CrappyBurgerItem extends TooltipItem {

    public CrappyBurgerItem(Settings settings) {
        super(settings);
    }

    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        ItemStack out = super.finishUsing(stack, world, user);

        if (world.isClient) return out;
        if (!(world instanceof ServerWorld sw)) return out;
        if (!(user instanceof ServerPlayerEntity sp)) return out;

        teleportForward(sw, sp, 8.0);
        return out;
    }

    private static void teleportForward(ServerWorld sw, ServerPlayerEntity sp, double maxDist) {
        Vec3d look = sp.getRotationVec(1.0f).normalize();
        Vec3d start = sp.getPos();

        // try from far -> near so you get the full distance when possible
        for (double d = maxDist; d >= 1.0; d -= 0.5) {
            Vec3d dest = start.add(look.multiply(d));

            // small up adjustment attempts (prevents faceplant into slabs)
            if (tryTeleport(sw, sp, dest)) return;
            if (tryTeleport(sw, sp, dest.add(0, 0.75, 0))) return;
            if (tryTeleport(sw, sp, dest.add(0, 1.25, 0))) return;
        }

        // fail: tiny feedback
        sw.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 0.6f, 0.6f);
    }

    private static boolean tryTeleport(ServerWorld sw, ServerPlayerEntity sp, Vec3d dest) {
        var box = sp.getBoundingBox().offset(dest.subtract(sp.getPos()));
        if (!sw.isSpaceEmpty(sp, box)) return false;

        sw.spawnParticles(ParticleTypes.PORTAL, sp.getX(), sp.getY() + 0.8, sp.getZ(), 24, 0.35, 0.35, 0.35, 0.02);
        sp.requestTeleport(dest.x, dest.y, dest.z);
        sw.spawnParticles(ParticleTypes.PORTAL, dest.x, dest.y + 0.8, dest.z, 24, 0.35, 0.35, 0.35, 0.02);

        sw.playSound(null, sp.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.9f, 1.1f);
        return true;
    }
}