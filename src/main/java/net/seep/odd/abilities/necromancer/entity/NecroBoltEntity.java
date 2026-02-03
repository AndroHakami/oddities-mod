// src/main/java/net/seep/odd/abilities/necromancer/projectile/NecroBoltEntity.java
package net.seep.odd.abilities.necromancer.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

import org.joml.Vector3f;

import net.seep.odd.Oddities;
import net.seep.odd.abilities.power.NecromancerPower;
import net.seep.odd.entity.ModEntities;
import net.seep.odd.item.ModItems;

public class NecroBoltEntity extends ThrownItemEntity {

    // ✅ DamageType key (requires JSON in data/odd/damage_type/necrobolt.json)
    public static final RegistryKey<DamageType> NECRO_BOLT_DAMAGE =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier(Oddities.MOD_ID, "necrobolt"));

    private static final DustParticleEffect MAGENTA =
            new DustParticleEffect(new Vector3f(1f, 0f, 1f), 1.15f);

    // Tuning
    private static final float DAMAGE = 4.0f; // 2 hearts
    private static final int MAX_LIFE_T = 20 * 6;

    public NecroBoltEntity(EntityType<? extends NecroBoltEntity> type, World world) {
        super(type, world);
    }
    private DamageSource necroBoltSource(Entity owner) {
        RegistryEntry<DamageType> type = getWorld()
                .getRegistryManager()
                .get(RegistryKeys.DAMAGE_TYPE)
                .entryOf(NECRO_BOLT_DAMAGE); // MUST be RegistryKey<DamageType>

        // source = projectile, attacker = owner (if present)
        return (owner != null) ? new DamageSource(type, this, owner) : new DamageSource(type, this);
    }


    public NecroBoltEntity(World world, LivingEntity owner) {
        super(ModEntities.NECRO_BOLT, owner, world);
    }

    @Override
    protected Item getDefaultItem() {
        // This is only used for rendering (like your Ice projectile)
        return ModItems.NECRO_BOLT;
    }

    // ✅ Straight-line flight
    @Override
    protected float getGravity() {
        return 0.0f;
    }

    @Override
    public void tick() {
        super.tick();

        if (this.age > MAX_LIFE_T) {
            discard();
            return;
        }

        // ✅ trail (tiny + consistent)
        if (this.getWorld().isClient) {
            spawnTrailClient();
        } else if ((this.age & 1) == 0 && this.getWorld() instanceof ServerWorld sw) {
            // low-frequency server trail so others see it too
            sw.spawnParticles(MAGENTA, getX(), getY() + 0.05, getZ(), 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    private void spawnTrailClient() {
        World w = this.getWorld();
        Vec3d v = this.getVelocity();

        // spawn a couple along its path for a smoother trail
        for (int i = 0; i < 2; i++) {
            double t = i / 2.0;
            double x = getX() - v.x * t * 0.35;
            double y = getY() + 0.05 - v.y * t * 0.35;
            double z = getZ() - v.z * t * 0.35;
            w.addParticle(MAGENTA, true, x, y, z, 0, 0, 0);
        }
    }

    @Override
    protected void onEntityHit(EntityHitResult hit) {
        super.onEntityHit(hit);

        if (getWorld().isClient) return;

        Entity e = hit.getEntity();
        Entity owner = getOwner();

        if (e instanceof LivingEntity le && le.isAlive()) {
            DamageSource src = necroBoltSource(owner);
            le.damage(src, DAMAGE);

            // ✅ Horde focus hook
            if (owner instanceof ServerPlayerEntity sp) {
                NecromancerPower.setFocus(sp, le);
            }
        }

        // ✅ small impact burst
        if (getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(MAGENTA, getX(), getY() + 0.12, getZ(), 14, 0.10, 0.06, 0.10, 0.0);
            sw.spawnParticles(ParticleTypes.WITCH, getX(), getY() + 0.12, getZ(), 6, 0.08, 0.05, 0.08, 0.0);
            sw.spawnParticles(ParticleTypes.SMOKE, getX(), getY() + 0.10, getZ(), 4, 0.04, 0.02, 0.04, 0.0);

            sw.playSound(null, getX(), getY(), getZ(),
                    SoundEvents.ENTITY_ENDER_EYE_DEATH, SoundCategory.PLAYERS,
                    0.45f, 1.35f);
        }

        discard();
    }


    @Override
    protected void onCollision(HitResult hitResult) {
        super.onCollision(hitResult);

        if (getWorld().isClient) return;

        if (getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(MAGENTA, getX(), getY() + 0.1, getZ(), 8, 0.10, 0.05, 0.10, 0.0);
        }

        discard();
    }
}
