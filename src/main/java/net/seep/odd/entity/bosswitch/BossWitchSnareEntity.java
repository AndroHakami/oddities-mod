package net.seep.odd.entity.bosswitch;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

public final class BossWitchSnareEntity extends Entity {
    private static final float RADIUS = 3.0f;
    private static final int WARNING_TICKS = 12;
    private static final int ROOT_TICKS = 60;

    private UUID ownerUuid;
    private int life = 0;
    private final Set<UUID> snared = new HashSet<>();

    public BossWitchSnareEntity(EntityType<? extends BossWitchSnareEntity> type, World world) {
        super(type, world);
        this.noClip = true;
    }

    public void setOwnerUuid(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    @Override
    protected void initDataTracker() {
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.getWorld() instanceof ServerWorld sw)) return;

        this.life++;
        spawnWarningParticles(sw);

        if (this.life == WARNING_TICKS) {
            activate(sw);
        }

        if (this.life > WARNING_TICKS && this.life <= WARNING_TICKS + ROOT_TICKS) {
            holdSnaredPlayers(sw);
        }

        if (this.life >= WARNING_TICKS + ROOT_TICKS + 12) {
            this.discard();
        }
    }

    private void spawnWarningParticles(ServerWorld sw) {
        double progress = Math.min(1.0D, this.life / (double) WARNING_TICKS);
        double ring = RADIUS * (1.0D - 0.18D * progress);

        for (int i = 0; i < 22; i++) {
            double a = (Math.PI * 2.0D * i) / 22.0D;
            double x = this.getX() + Math.cos(a) * ring;
            double z = this.getZ() + Math.sin(a) * ring;
            sw.spawnParticles(ParticleTypes.DRAGON_BREATH, x, this.getY() + 0.05D, z, 1, 0.0D, 0.02D, 0.0D, 0.0D);
        }
    }

    private void activate(ServerWorld sw) {
        Entity owner = this.ownerUuid == null ? null : sw.getEntity(this.ownerUuid);
        Box box = this.getBoundingBox().expand(RADIUS, 1.5D, RADIUS);

        for (PlayerEntity player : sw.getEntitiesByClass(PlayerEntity.class, box, p -> p.isAlive() && !p.isCreative() && !p.isSpectator())) {
            if (player.squaredDistanceTo(this.getX(), this.getY(), this.getZ()) > (RADIUS * RADIUS)) continue;

            this.snared.add(player.getUuid());
            player.damage(sw.getDamageSources().indirectMagic(this, owner), 2.0f);
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, ROOT_TICKS + 5, 6, false, true, true));
            sw.spawnParticles(ParticleTypes.WITCH, player.getX(), player.getBodyY(0.5D), player.getZ(),
                    16, 0.25D, 0.28D, 0.25D, 0.02D);
        }
    }

    private void holdSnaredPlayers(ServerWorld sw) {
        Iterator<UUID> it = this.snared.iterator();
        while (it.hasNext()) {
            UUID uuid = it.next();
            Entity e = sw.getEntity(uuid);
            if (!(e instanceof PlayerEntity player) || !player.isAlive()) {
                it.remove();
                continue;
            }

            player.setVelocity(player.getVelocity().x * 0.08D, Math.min(player.getVelocity().y, 0.0D), player.getVelocity().z * 0.08D);
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 5, 6, false, false, false));

            if (this.age % 6 == 0) {
                sw.spawnParticles(ParticleTypes.DRAGON_BREATH, player.getX(), player.getY() + 0.1D, player.getZ(),
                        4, 0.35D, 0.02D, 0.35D, 0.0D);
            }
        }
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        this.life = nbt.getInt("Life");
        this.ownerUuid = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null;
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putInt("Life", this.life);
        if (this.ownerUuid != null) nbt.putUuid("Owner", this.ownerUuid);
    }
}