package net.seep.odd.expeditions.rottenroots.boggy;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.stat.Stats;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.entity.player.PlayerEntity;

import java.util.List;
import java.util.function.Predicate;

public class BoggyBoatItem extends Item {
    private static final Predicate<Entity> RIDERS =
            e -> e.isAlive() && e.isCollidable();

    private final EntityType<? extends BoatEntity> entityType;

    public BoggyBoatItem(EntityType<? extends BoatEntity> entityType, Settings settings) {
        super(settings);
        this.entityType = entityType;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        HitResult hit = raycast(world, user, RaycastContext.FluidHandling.ANY);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return TypedActionResult.pass(stack);
        }

        // Prevent placing inside entities in front of you (same vibe as vanilla BoatItem)
        Vec3d look = user.getRotationVec(1.0F);
        Box box = user.getBoundingBox().stretch(look.multiply(5.0D)).expand(1.0D);
        List<Entity> list = world.getOtherEntities(user, box, RIDERS);
        if (!list.isEmpty()) {
            Vec3d eyes = user.getEyePos();
            for (Entity e : list) {
                Box eb = e.getBoundingBox().expand(e.getTargetingMargin());
                if (eb.contains(eyes)) {
                    return TypedActionResult.pass(stack);
                }
            }
        }

        BlockHitResult bhr = (BlockHitResult) hit;
        BoatEntity boat = this.entityType.create(world);
        if (boat == null) {
            return TypedActionResult.fail(stack);
        }

        Vec3d pos = bhr.getPos();
        boat.refreshPositionAndAngles(pos.x, pos.y, pos.z, user.getYaw(), 0.0F);

        // Let vanilla decide if it’s valid space
        if (!world.isSpaceEmpty(boat, boat.getBoundingBox())) {
            return TypedActionResult.fail(stack);
        }

        if (!world.isClient) {
            world.spawnEntity(boat);
            if (!user.getAbilities().creativeMode) {
                stack.decrement(1);
            }
        }

        user.incrementStat(Stats.USED.getOrCreateStat(this));
        return TypedActionResult.success(stack, world.isClient);
    }
}