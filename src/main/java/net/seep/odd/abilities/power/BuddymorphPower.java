package net.seep.odd.abilities.power;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.Oddities;
import net.seep.odd.abilities.buddymorph.BuddymorphData;
import net.seep.odd.abilities.buddymorph.BuddymorphNet;
import net.seep.odd.abilities.buddymorph.client.BuddymorphClient;
import net.seep.odd.sound.ModSounds;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

public final class BuddymorphPower implements Power {
    @Override public String id() { return "buddymorph"; }
    @Override public String displayName() { return "Buddyform"; }
    @Override public boolean hasSlot(String slot) { return "primary".equals(slot) || "secondary".equals(slot); }
    @Override public long cooldownTicks() { return 1200; }
    @Override public long secondaryCooldownTicks() { return 5; }
    @Override public long thirdCooldownTicks() { return 0; }

    @Override
    public Identifier iconTexture(String slot) {
        return switch (slot) {
            case "primary"   -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/buddymorph_melody.png");
            case "secondary" -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/buddymorph_morph.png");
            default          -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/ability_default.png");
        };
    }

    @Override public String longDescription() {
        return "Shapeshift into any non-hostile mob you’ve befriended!";
    }
    @Override public String slotLongDescription(String slot) {
        return switch (slot) {
            case "primary"   -> "Sing a lullaby to befriend non-hostile mob, and receive a gift!";
            case "secondary" -> "Morph into a befriended mob";
            default -> "";
        };
    }
    @Override public String slotTitle(String slot) {
        return switch (slot) {
            case "primary" -> "FRIENDLY MELODY";
            case "secondary" -> "BUDDY-MORPH";
            default -> Power.super.slotTitle(slot);
        };
    }
    @Override public Identifier portraitTexture() { return new Identifier(Oddities.MOD_ID, "textures/gui/overview/buddymorph.png"); }

    /* ----- Primary: befriend ----- */
    private static final int MELODY_RANGE = 5;
    private static final int MELODY_TICKS = 60;
    private static final List<ItemStack> CUTE_DROPS = List.of(
            new ItemStack(Items.COOKIE, 3),
            new ItemStack(Items.SWEET_BERRIES, 4),
            new ItemStack(Items.CAKE, 1),
            new ItemStack(Items.BREAD, 2),
            new ItemStack(Items.HONEY_BOTTLE, 1)
    );

    @Override
    public void activate(ServerPlayerEntity p) {
        ServerWorld sw = (ServerWorld) p.getWorld();

        sw.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.MELODY, SoundCategory.PLAYERS, 1.0f, 1.0f);
        BuddymorphNet.s2cMelody(p, MELODY_TICKS);

        BuddymorphData data = BuddymorphData.get(sw);

        LivingEntity best = nearestNonHostile(sw, p, MELODY_RANGE);
        if (best == null) {
            p.sendMessage(Text.literal("No curious critters nearby..."), true);
            aura(sw, p, 0.6, 0.2);
            return;
        }

        Identifier typeId = Registries.ENTITY_TYPE.getId(best.getType());
        if (typeId == null) {
            p.sendMessage(Text.literal("That one can’t befriend (unknown type)."), true);
            aura(sw, p, 0.6, 0.2);
            return;
        }

        boolean added = data.addBuddy(p.getUuid(), typeId);

        if (added) {
            ItemStack gift = CUTE_DROPS.get(sw.random.nextInt(CUTE_DROPS.size())).copy();
            ItemEntity ei = new ItemEntity(sw, p.getX(), p.getY() + 1.2, p.getZ(), gift);
            ei.setToDefaultPickupDelay();
            sw.spawnEntity(ei);

            hearts(sw, best.getPos());
            aura(sw, p, 1.2, 0.55);
            p.sendMessage(Text.literal("Befriended: " + best.getType().getName().getString() + " (3 charges)"), true);
        } else {
            int left = BuddymorphData.get(sw).getCharges(p.getUuid(), typeId);
            p.sendMessage(Text.literal("Already befriended. Charges left: " + left), true);
            aura(sw, p, 0.8, 0.35);
        }

        // Push full list + charges
        LinkedHashMap<Identifier,Integer> map = data.getListWithCharges(p.getUuid());
        BuddymorphNet.s2cUpdateMenu(p, map);
    }

    private static LivingEntity nearestNonHostile(ServerWorld sw, ServerPlayerEntity p, double r) {
        Box box = new Box(p.getX()-r, p.getY()-r, p.getZ()-r, p.getX()+r, p.getY()+r, p.getZ()+r);
        double best = Double.MAX_VALUE;
        LivingEntity res = null;
        for (LivingEntity le : sw.getEntitiesByType(TypeFilter.instanceOf(LivingEntity.class), box, Entity::isAlive)) {
            if (!isNonHostile(le)) continue;
            double d = le.squaredDistanceTo(p);
            if (d < best) { best = d; res = le; }
        }
        return res;
    }
    private static boolean isNonHostile(LivingEntity e) {
        if (e instanceof PlayerEntity) return false;
        if (e instanceof HostileEntity) return false;
        return e.getType().getSpawnGroup() != SpawnGroup.MONSTER;
    }

    private static void hearts(ServerWorld sw, Vec3d at) {
        for (int i = 0; i < 12; i++) {
            double a = (i / 12.0) * Math.PI * 2.0, rr = 0.6;
            sw.spawnParticles(ParticleTypes.HEART, at.x + Math.cos(a)*rr, at.y + 0.6, at.z + Math.sin(a)*rr, 1, 0, 0, 0, 0);
        }
    }
    private static void aura(ServerWorld sw, ServerPlayerEntity p, double rr, double y) {
        Vec3d c = p.getPos();
        for (int i = 0; i < 24; i++) {
            double t = i/24.0 * Math.PI*2.0;
            sw.spawnParticles(ParticleTypes.NOTE, c.x + Math.cos(t)*rr, c.y + y, c.z + Math.sin(t)*rr, 1, 0, 0, 0, 0);
        }
        return;
    }

    /* ----- Secondary: open picker ----- */
    @Override
    public void activateSecondary(ServerPlayerEntity p) {
        ServerWorld sw = (ServerWorld) p.getWorld();
        LinkedHashMap<Identifier,Integer> map = BuddymorphData.get(sw).getListWithCharges(p.getUuid());
        BuddymorphNet.s2cOpenMenu(p, map);
    }

    /* ----- Morph via Identity commands (spends a charge) ----- */
    public static void serverMorphTo(ServerPlayerEntity p, Optional<Identifier> typeId) {
        MinecraftServer s = p.getServer(); if (s == null) return;
        ServerCommandSource src = p.getCommandSource().withSilent();

        if (typeId.isEmpty()) {
            s.getCommandManager().executeWithPrefix(src, "identity unequip @s");
            return;
        }

        Identifier id = typeId.get();
        BuddymorphData data = BuddymorphData.get(p.getServerWorld());

        if (!data.hasBuddy(p.getUuid(), id)) {
            p.sendMessage(Text.literal("That buddy isn’t befriended yet."), true);
            return;
        }

        boolean spent = data.consumeCharge(p.getUuid(), id);
        if (!spent) {
            p.sendMessage(Text.literal("No charges left for " + id + ". Befriend them again!"), true);
            BuddymorphNet.s2cUpdateMenu(p, data.getListWithCharges(p.getUuid()));
            return;
        }

        s.getCommandManager().executeWithPrefix(src, "identity equip @s " + id);

        // Update menu (may vanish if last charge was spent)
        BuddymorphNet.s2cUpdateMenu(p, data.getListWithCharges(p.getUuid()));
    }

    /* client bootstrap passthrough */
    @Environment(EnvType.CLIENT)
    public static final class Client {
        public static void init() { BuddymorphClient.init(); }
    }
}
