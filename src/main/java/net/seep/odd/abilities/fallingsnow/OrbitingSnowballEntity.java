// src/main/java/net/seep/odd/abilities/fallingsnow/OrbitingSnowballEntity.java
package net.seep.odd.abilities.fallingsnow;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.seep.odd.entity.ModEntities;

public class OrbitingSnowballEntity extends ThrownItemEntity {
    private static final TrackedData<Boolean> BIG =
            DataTracker.registerData(OrbitingSnowballEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private static final double FRONT_OFFSET = 0.9;
    private static final double VERTICAL_BOB = 0.10;

    public OrbitingSnowballEntity(EntityType<? extends ThrownItemEntity> type, World world) {
        super(type, world);
        this.setNoGravity(true);
        this.noClip = true;
    }

    public OrbitingSnowballEntity(World world, LivingEntity owner) {
        super(ModEntities.ORBITING_SNOWBALL, owner, world);
        this.setNoGravity(true);
        this.noClip = true;
        this.setItem(Items.SNOWBALL.getDefaultStack());
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(BIG, Boolean.FALSE);
    }

    public void setBig(boolean big) {
        this.dataTracker.set(BIG, big);
        if (big) this.setItem(Items.SNOW_BLOCK.getDefaultStack()); // render as a block
    }

    @Override
    protected Item getDefaultItem() {
        return this.dataTracker.get(BIG) ? Items.SNOW_BLOCK : Items.SNOWBALL;
    }

    @Override
    public void tick() {
        super.tick();
        this.setNoGravity(true);
        this.noClip = true;
        this.setVelocity(Vec3d.ZERO);

        LivingEntity owner = (LivingEntity) getOwner();
        if (owner == null || !owner.isAlive()) { this.discard(); return; }

        // sit directly in front of the player with a tiny bob + slow spin
        Vec3d eye = owner.getEyePos();
        Vec3d fwd = owner.getRotationVec(1.0f).normalize();
        double bob = Math.sin(this.age * 0.25) * VERTICAL_BOB;
        Vec3d pos = eye.add(fwd.multiply(FRONT_OFFSET)).add(0, bob, 0);

        this.refreshPositionAndAngles(pos.x, pos.y, pos.z, owner.getYaw() + (this.age * 6f), 0f);
    }

    /* purely visual — never collide/hit while charging */
    public boolean collides() { return false; }
    @Override protected boolean canHit(Entity entity) { return false; }
}
