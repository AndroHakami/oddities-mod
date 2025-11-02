package net.seep.odd.expeditions.portal;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.seep.odd.Oddities;

public class ExpeditionPortalEntity extends Entity {
    public static final Identifier ID = new Identifier(Oddities.MOD_ID, "expedition_portal");
    public static EntityType<ExpeditionPortalEntity> TYPE;

    private int life;
    private int maxLife = 200; // default 10s
    private net.minecraft.registry.RegistryKey<World> targetWorld;

    public ExpeditionPortalEntity(EntityType<? extends ExpeditionPortalEntity> type, World world) {
        super(type, world);
        noClip = true;
    }

    public static void register() {
        TYPE = Registry.register(Registries.ENTITY_TYPE, ID,
                FabricEntityTypeBuilder.create(SpawnGroup.MISC, ExpeditionPortalEntity::new)
                        .dimensions(EntityDimensions.fixed(1.0f, 2.0f))
                        .trackRangeBlocks(64).trackedUpdateRate(1).build());
    }

    public static void spawn(ServerWorld world, Vec3d pos, net.minecraft.registry.RegistryKey<World> target, int lifetimeTicks) {
        ExpeditionPortalEntity e = new ExpeditionPortalEntity(TYPE, world);
        e.refreshPositionAndAngles(pos.x, pos.y, pos.z, 0, 0);
        e.maxLife = Math.max(40, lifetimeTicks);
        e.targetWorld = target;
        world.spawnEntity(e);
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) return;

        ServerWorld sw = (ServerWorld) getWorld();

        // pretty particles
        for (int i = 0; i < 12; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = 0.8 + random.nextDouble() * 0.6;
            double x = getX() + Math.cos(a) * r;
            double z = getZ() + Math.sin(a) * r;
            sw.spawnParticles(ParticleTypes.PORTAL, x, getY() + 1.0, z, 1, 0, 0, 0, 0);
        }

        life++;
        if (life >= maxLife) {
            // teleport nearby players and then remove
            var tw = sw.getServer().getWorld(targetWorld);
            if (tw != null) {
                var spawn = tw.getSpawnPos();
                getWorld().getEntitiesByClass(ServerPlayerEntity.class, getBoundingBox().expand(2.0), p -> true)
                        .forEach(p -> p.teleport(tw, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, p.getYaw(), p.getPitch()));
            }
            discard();
        }
    }

    @Override public void readCustomDataFromNbt(NbtCompound nbt) {
        maxLife = nbt.getInt("MaxLife");
    }
    @Override public void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putInt("MaxLife", maxLife);
    }
    @Override protected void initDataTracker() {}
    @Override public boolean shouldSave() { return false; }
    public boolean collides() { return false; }
    @Override public EntitySpawnS2CPacket createSpawnPacket() {
        return new EntitySpawnS2CPacket(this);
    }
}
