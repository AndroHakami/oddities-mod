// src/main/java/net/seep/odd/entity/necromancer/AbstractCorpseEntity.java
package net.seep.odd.entity.necromancer;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;

import java.util.UUID;

import net.seep.odd.abilities.power.NecromancerPower;

public abstract class AbstractCorpseEntity extends Entity {

    private UUID owner;

    protected AbstractCorpseEntity(EntityType<?> type, World world) {
        super(type, world);
        this.noClip = true;
    }

    public UUID getOwner() { return owner; }
    public void setOwner(UUID owner) { this.owner = owner; }

    @Override
    public void tick() {
        super.tick();

        // no movement / physics
        this.setVelocity(Vec3d.ZERO);
        this.move(MovementType.SELF, Vec3d.ZERO);

        // despawn after 30s
        if (!this.getWorld().isClient) {
            if (this.age >= NecromancerPower.CORPSE_LIFE_T) {
                this.discard();
            }
        }
    }

    public float alpha(float tickDelta) {
        int life = NecromancerPower.CORPSE_LIFE_T;
        int fade = 40; // last 2s fade
        int a = this.age;

        if (a < life - fade) return 1.0f;

        float t = (a + tickDelta - (life - fade)) / (float)fade;
        t = Math.max(0f, Math.min(1f, t));
        return 1.0f - t;
    }

    @Override
    protected void initDataTracker() {}

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.containsUuid("Owner")) {
            owner = nbt.getUuid("Owner");
        }
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        if (owner != null) {
            nbt.putUuid("Owner", owner);
        }
    }

    @Override
    public boolean isCollidable() { return false; }

    @Override
    public boolean isAttackable() { return false; }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() {
        return new EntitySpawnS2CPacket(this);
    }
}
