package net.seep.odd.abilities.power;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.VineBlock;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.BoneMealItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import net.seep.odd.Oddities;
import net.seep.odd.abilities.druid.DruidData;
import net.seep.odd.abilities.druid.DruidForm;
import net.seep.odd.abilities.druid.DruidNet;
import net.seep.odd.abilities.druid.client.DruidClient;
import net.seep.odd.status.ModStatusEffects;

import org.joml.Vector3f;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DruidPower implements Power {
    public static final long FORM_DEATH_COOLDOWN_TICKS = 20L * 20L;

    private static final UUID NO_ARMOR_UUID = UUID.fromString("7a7fca47-5e48-4d1a-a22b-6f2d1b3a2a01");
    private static final UUID NO_TOUGH_UUID = UUID.fromString("1b1c3f4c-2f61-4b17-8d1d-3c12d5d63a02");

    // --- POWERLESS enforcement: revert once on transition ---
    private static boolean tickRegistered = false;
    private static final Set<UUID> POWERLESS_HANDLED = ConcurrentHashMap.newKeySet();

    public DruidPower() {
        if (!tickRegistered) {
            tickRegistered = true;

            ServerTickEvents.END_SERVER_TICK.register(server -> {
                for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {

                    // Only care while Druid is the selected/active power
                    String currentId = net.seep.odd.abilities.PowerAPI.get(p);
                    if (!"druid".equals(currentId)) {
                        POWERLESS_HANDLED.remove(p.getUuid());
                        continue;
                    }

                    boolean powerless = isPowerless(p);

                    if (powerless) {
                        // ✅ do it ONCE per POWERLESS application
                        if (POWERLESS_HANDLED.add(p.getUuid())) {
                            forceHumanForm(p);
                            p.sendMessage(Text.literal("§cYou are powerless."), true);
                        }
                    } else {
                        // Effect gone => allow future transitions to trigger again
                        POWERLESS_HANDLED.remove(p.getUuid());
                    }
                }
            });
        }
    }

    @Override public String id() { return "druid"; }
    @Override public String displayName() { return "Druid"; }
    @Override public boolean hasSlot(String slot) { return "primary".equals(slot) || "secondary".equals(slot); }

    @Override public long cooldownTicks() { return 0; }
    @Override public long secondaryCooldownTicks() { return 8; }
    @Override public long thirdCooldownTicks() { return 0; }

    @Override
    public Identifier iconTexture(String slot) {
        return switch (slot) {
            case "primary" -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/druid_shift.png");
            case "secondary" -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/druid_blossom.png");
            default -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/ability_default.png");
        };
    }

    @Override public String slotTitle(String slot) {
        return switch (slot) {
            case "primary" -> "SHAPE SHIFT";
            case "secondary" -> "BLOSSOMING TOUCH";
            default -> Power.super.slotTitle(slot);
        };
    }

    @Override public String slotLongDescription(String slot) {
        return switch (slot) {
            case "primary" -> "Open a wheel and choose a form.";
            case "secondary" -> "Touch blocks to trigger strong growth.";
            default -> "";
        };
    }

    @Override public Identifier portraitTexture() {
        return new Identifier(Oddities.MOD_ID, "textures/gui/overview/druid.png");
    }

    /* ----- POWERLESS + command helpers (server-side perms, no OP needed) ----- */

    private static boolean isPowerless(ServerPlayerEntity player) {
        return player != null && player.hasStatusEffect(ModStatusEffects.POWERLESS);
    }

    /** Runs a command with SERVER permission level, but with @s targeting the player. */
    private static void runCommandAsServerPlayer(ServerPlayerEntity p, String command) {
        MinecraftServer s = p.getServer();
        if (s == null) return;

        ServerCommandSource src = s.getCommandSource()
                .withEntity(p)
                .withSilent();

        s.getCommandManager().executeWithPrefix(src, command);
    }

    private static void forceHumanForm(ServerPlayerEntity p) {
        ServerWorld sw = p.getServerWorld();
        DruidData data = DruidData.get(sw);

        // If already human (data-wise), we still ensure identity is unequipped once via serverRevertToHuman
        if (data.getCurrentForm(p.getUuid()) != DruidForm.HUMAN) {
            data.setCurrentForm(p.getUuid(), DruidForm.HUMAN);
        }

        // No FX here (keeps it clean / non-spammy)
        serverRevertToHuman(p, false);
        DruidNet.s2cForm(p, DruidForm.HUMAN);
    }

    private static boolean blockIfPowerless(ServerPlayerEntity p) {
        if (!isPowerless(p)) return false;

        // ✅ don’t re-run revert every press while POWERLESS is still active
        if (POWERLESS_HANDLED.add(p.getUuid())) {
            forceHumanForm(p);
            p.sendMessage(Text.literal("§cYou are powerless."), true);
        }
        return true;
    }

    @Override
    public void forceDisable(ServerPlayerEntity player) {
        // ✅ don’t spam if already human
        ServerWorld sw = player.getServerWorld();
        DruidData data = DruidData.get(sw);
        if (data.getCurrentForm(player.getUuid()) != DruidForm.HUMAN) {
            forceHumanForm(player);
        }
    }

    /* ---------------- Primary: open wheel ---------------- */
    @Override
    public void activate(ServerPlayerEntity p) {
        if (blockIfPowerless(p)) return;

        ServerWorld sw = p.getServerWorld();
        DruidData data = DruidData.get(sw);

        long now = sw.getTime();
        long until = data.getDeathCooldownUntil(p.getUuid());
        if (until > now) {
            int sec = (int) Math.ceil((until - now) / 20.0);
            p.sendMessage(Text.literal("Shapeshift locked for " + sec + "s."), true);
            sw.playSound(null, p.getX(), p.getY(), p.getZ(),
                    SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 0.6f, 0.7f);
            return;
        }

        DruidNet.s2cOpenWheel(p);
        sw.playSound(null, p.getX(), p.getY(), p.getZ(),
                SoundEvents.UI_BUTTON_CLICK.value(), SoundCategory.PLAYERS, 0.6f, 1.2f);
    }

    /* ---------------- Secondary: Blossoming Touch ---------------- */
    @Override
    public void activateSecondary(ServerPlayerEntity p) {
        if (blockIfPowerless(p)) return;

        ServerWorld sw = p.getServerWorld();

        HitResult hr = p.raycast(5.0, 0.0f, true);
        if (!(hr instanceof BlockHitResult bhr)) return;

        BlockPos hitPos = bhr.getBlockPos();
        Direction side = bhr.getSide();
        BlockState hitState = sw.getBlockState(hitPos);

        boolean did = false;

        if (side.getAxis().isHorizontal()) {
            did |= growVinesDown(sw, hitPos, side, 5);
        }

        if (sw.getRegistryKey() == World.NETHER) {
            did |= netherBloom(sw, hitPos, side);
        } else if (hitState.isOf(Blocks.MYCELIUM)) {
            did |= spawnMushrooms(sw, hitPos.up(), 10);
        } else if (hitState.isOf(Blocks.SAND) || hitState.isOf(Blocks.RED_SAND)) {
            did |= spawnCactus(sw, hitPos.up(), 6);
        }

        if (!did && sw.getFluidState(hitPos).isIn(net.minecraft.registry.tag.FluidTags.WATER)) {
            did |= underwaterBloom(sw, hitPos);
        }

        if (!did) {
            did |= bonemealArea(sw, hitPos);
        }

        if (did) {
            blossomFx(sw, bhr.getPos());
            sw.playSound(null, bhr.getPos().x, bhr.getPos().y, bhr.getPos().z,
                    SoundEvents.ITEM_BONE_MEAL_USE, SoundCategory.PLAYERS, 0.9f, 1.05f);
        }
    }

    private static boolean bonemealArea(ServerWorld sw, BlockPos center) {
        boolean did = false;
        for (int pass = 0; pass < 2; pass++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos p = center.add(dx, 0, dz);
                    ItemStack meal = new ItemStack(Items.BONE_MEAL, 1);
                    if (BoneMealItem.useOnFertilizable(meal, sw, p)) did = true;

                    ItemStack meal2 = new ItemStack(Items.BONE_MEAL, 1);
                    if (BoneMealItem.useOnFertilizable(meal2, sw, p.up())) did = true;
                }
            }
        }
        return did;
    }

    private static boolean growVinesDown(ServerWorld sw, BlockPos hitPos, Direction side, int length) {
        BlockPos start = hitPos.offset(side);
        if (!sw.getBlockState(start).isAir()) return false;

        boolean did = false;
        Direction attach = side.getOpposite();

        for (int i = 0; i < length; i++) {
            BlockPos at = start.down(i);
            if (!sw.getBlockState(at).isAir()) break;

            BlockState vine = Blocks.VINE.getDefaultState()
                    .with(VineBlock.getFacingProperty(attach), true);

            if (vine.canPlaceAt(sw, at)) {
                sw.setBlockState(at, vine, 3);
                did = true;
            }
        }
        return did;
    }

    private static boolean underwaterBloom(ServerWorld sw, BlockPos hitPos) {
        boolean did = false;
        for (int i = 0; i < 10; i++) {
            int dx = sw.random.nextInt(5) - 2;
            int dz = sw.random.nextInt(5) - 2;
            BlockPos p = hitPos.add(dx, 0, dz);

            if (!sw.getFluidState(p).isIn(net.minecraft.registry.tag.FluidTags.WATER)) continue;
            if (!sw.getBlockState(p).isAir() && !sw.getBlockState(p).isOf(Blocks.WATER)) continue;

            BlockPos below = p.down();
            if (sw.getBlockState(below).isAir()) continue;

            BlockState seagrass = Blocks.SEAGRASS.getDefaultState();
            if (seagrass.canPlaceAt(sw, p)) {
                sw.setBlockState(p, seagrass, 3);
                did = true;
            }
        }
        return did;
    }

    private static boolean netherBloom(ServerWorld sw, BlockPos hitPos, Direction side) {
        boolean did = false;

        BlockPos base = (side == Direction.UP) ? hitPos : hitPos.offset(side).down();
        BlockPos start = base.up();

        for (int i = 0; i < 10; i++) {
            int dx = sw.random.nextInt(5) - 2;
            int dz = sw.random.nextInt(5) - 2;
            BlockPos p = start.add(dx, 0, dz);

            if (!sw.getBlockState(p).isAir()) continue;

            BlockState below = sw.getBlockState(p.down());
            BlockState place;
            if (below.isOf(Blocks.CRIMSON_NYLIUM)) {
                place = Blocks.CRIMSON_FUNGUS.getDefaultState();
            } else if (below.isOf(Blocks.WARPED_NYLIUM)) {
                place = Blocks.WARPED_FUNGUS.getDefaultState();
            } else {
                place = (sw.random.nextBoolean() ? Blocks.RED_MUSHROOM : Blocks.BROWN_MUSHROOM).getDefaultState();
            }

            if (place.canPlaceAt(sw, p)) {
                sw.setBlockState(p, place, 3);
                did = true;
            }
        }

        return did;
    }

    private static boolean spawnMushrooms(ServerWorld sw, BlockPos start, int tries) {
        boolean did = false;
        for (int i = 0; i < tries; i++) {
            int dx = sw.random.nextInt(7) - 3;
            int dz = sw.random.nextInt(7) - 3;
            BlockPos p = start.add(dx, 0, dz);
            if (!sw.getBlockState(p).isAir()) continue;

            BlockState place = (sw.random.nextBoolean() ? Blocks.RED_MUSHROOM : Blocks.BROWN_MUSHROOM).getDefaultState();
            if (place.canPlaceAt(sw, p)) {
                sw.setBlockState(p, place, 3);
                did = true;
            }
        }
        return did;
    }

    private static boolean spawnCactus(ServerWorld sw, BlockPos start, int tries) {
        boolean did = false;
        for (int i = 0; i < tries; i++) {
            int dx = sw.random.nextInt(7) - 3;
            int dz = sw.random.nextInt(7) - 3;
            BlockPos p = start.add(dx, 0, dz);
            if (!sw.getBlockState(p).isAir()) continue;

            BlockState cactus = Blocks.CACTUS.getDefaultState();
            if (cactus.canPlaceAt(sw, p)) {
                sw.setBlockState(p, cactus, 3);
                did = true;
            }
        }
        return did;
    }

    private static void blossomFx(ServerWorld sw, Vec3d at) {
        DustParticleEffect green = new DustParticleEffect(new Vector3f(0.25f, 0.95f, 0.35f), 1.2f);
        sw.spawnParticles(green, at.x, at.y + 0.1, at.z, 18, 0.25, 0.15, 0.25, 0.0);
        sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER, at.x, at.y + 0.1, at.z, 10, 0.2, 0.2, 0.2, 0.0);
    }

    /* ---------------- Server-side form switching ---------------- */

    public static void serverSetForm(ServerPlayerEntity p, DruidForm chosen) {
        if (isPowerless(p)) {
            // ✅ also only revert once per powerless phase
            if (POWERLESS_HANDLED.add(p.getUuid())) {
                forceHumanForm(p);
                p.sendMessage(Text.literal("§cYou are powerless."), true);
            }
            return;
        }

        ServerWorld sw = p.getServerWorld();
        DruidData data = DruidData.get(sw);

        long now = sw.getTime();
        if (data.getDeathCooldownUntil(p.getUuid()) > now) return;

        DruidForm current = data.getCurrentForm(p.getUuid());
        if (current == chosen) return;

        DruidForm.clearBuffs(p);

        if (chosen == DruidForm.HUMAN) {
            data.setCurrentForm(p.getUuid(), DruidForm.HUMAN);
            serverRevertToHuman(p, true);
            DruidNet.s2cForm(p, DruidForm.HUMAN);
            sw.playSound(null, p.getX(), p.getY(), p.getZ(),
                    SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.25f, 1.8f);
            return;
        }

        data.setStoredHumanHealth(p.getUuid(), p.getHealth());

        runCommandAsServerPlayer(p, "identity equip @s " + chosen.identityEntityId());

        applyNoArmor(p);
        chosen.applyBuffs(p);
        data.setCurrentForm(p.getUuid(), chosen);

        shiftFx(sw, p.getPos().add(0, 0.9, 0));
        DruidNet.s2cForm(p, chosen);
    }

    public static void serverRevertToHuman(ServerPlayerEntity p, boolean fx) {
        runCommandAsServerPlayer(p, "identity unequip @s");

        removeNoArmor(p);
        DruidForm.clearBuffs(p);

        if (fx) {
            ServerWorld sw = p.getServerWorld();
            shiftFx(sw, p.getPos().add(0, 0.9, 0));
            sw.playSound(null, p.getX(), p.getY(), p.getZ(),
                    SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.65f, 1.35f);
        }
    }

    private static void applyNoArmor(ServerPlayerEntity p) {
        EntityAttributeInstance armor = p.getAttributeInstance(EntityAttributes.GENERIC_ARMOR);
        if (armor != null && armor.getModifier(NO_ARMOR_UUID) == null) {
            armor.addTemporaryModifier(new EntityAttributeModifier(
                    NO_ARMOR_UUID, "odd_druid_no_armor", -1000.0, EntityAttributeModifier.Operation.ADDITION
            ));
        }

        EntityAttributeInstance tough = p.getAttributeInstance(EntityAttributes.GENERIC_ARMOR_TOUGHNESS);
        if (tough != null && tough.getModifier(NO_TOUGH_UUID) == null) {
            tough.addTemporaryModifier(new EntityAttributeModifier(
                    NO_TOUGH_UUID, "odd_druid_no_tough", -1000.0, EntityAttributeModifier.Operation.ADDITION
            ));
        }
    }

    private static void removeNoArmor(ServerPlayerEntity p) {
        EntityAttributeInstance armor = p.getAttributeInstance(EntityAttributes.GENERIC_ARMOR);
        if (armor != null) armor.removeModifier(NO_ARMOR_UUID);

        EntityAttributeInstance tough = p.getAttributeInstance(EntityAttributes.GENERIC_ARMOR_TOUGHNESS);
        if (tough != null) tough.removeModifier(NO_TOUGH_UUID);
    }

    private static void shiftFx(ServerWorld sw, Vec3d at) {
        DustParticleEffect green = new DustParticleEffect(new Vector3f(0.25f, 0.95f, 0.35f), 1.25f);
        sw.spawnParticles(green, at.x, at.y, at.z, 22, 0.35, 0.30, 0.35, 0.0);
        sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER, at.x, at.y, at.z, 10, 0.25, 0.25, 0.25, 0.0);
    }

    @Environment(EnvType.CLIENT)
    public static final class Client {
        public static void init() { DruidClient.init(); }
    }
}