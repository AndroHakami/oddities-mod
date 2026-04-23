// FILE: src/main/java/net/seep/odd/abilities/artificer/item/ArtificerVacuumItem.java
package net.seep.odd.abilities.artificer.item;

import net.minecraft.block.BlockState;
import net.minecraft.block.PlantBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityGroup;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

import net.seep.odd.abilities.PowerAPI;
import net.seep.odd.abilities.artificer.EssenceStorage;
import net.seep.odd.abilities.artificer.EssenceType;
import net.seep.odd.sound.ModSounds;
import net.seep.odd.status.ModStatusEffects;

import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.animatable.client.RenderProvider;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

public class ArtificerVacuumItem extends Item implements GeoItem {

    // ✅ durability (tune to taste)
    private static final int MAX_DURABILITY = 1012;

    // ✅ Only Artificer power can use this item
    private static final String REQUIRED_POWER_ID = "artificer";

    public ArtificerVacuumItem(Settings s) {
        super(s.maxCount(1).maxDamage(MAX_DURABILITY)); // ✅ damageable => Unbreaking/Mending compatible
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    // ✅ lets it be enchantable on table (books/anvil work regardless)
    @Override
    public int getEnchantability() {
        return 12;
    }

    // ✅ repairable by iron ingot
    @Override
    public boolean canRepair(ItemStack stack, ItemStack ingredient) {
        return ingredient.isOf(Items.IRON_INGOT) || super.canRepair(stack, ingredient);
    }

    /* ====== Tunables ====== */
    private static final double RANGE           = 6.5;
    private static final int    DRAIN_EVERY_T   = 5;     // every 0.25s
    private static final int    ADD_PER_TICK    = 2;
    private static final float  DOT_DAMAGE      = 1.0f;

    // Cone shape (half-angle)
    private static final double CONE_DEG        = 22.5;
    private static final double CONE_TAN        = Math.tan(Math.toRadians(CONE_DEG));

    // Block marching within the cone
    private static final double STEP            = 0.4;

    // Gaia absorption rules (no block breaking)
    private static final int GAIA_MAX_Y = 48;
    private static final int GAIA_MAX_LIGHT = 7;
    private static final int GAIA_MAX_SKY = 2;

    /* ---------- Power gate ---------- */

    private static boolean canUseVacuum(PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity sp)) return true; // client-side: let server be authoritative
        if (sp.hasStatusEffect(ModStatusEffects.POWERLESS)) return false;
        String id = PowerAPI.get(sp);
        return REQUIRED_POWER_ID.equals(id);
    }

    private static TypedActionResult<ItemStack> denyUse(World world, PlayerEntity user, ItemStack stack) {
        if (!world.isClient) {
            user.sendMessage(Text.literal("Only an Artificer can use this."), true);
            if (user instanceof ServerPlayerEntity sp) {
                sp.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 0.6f, 0.6f);
            }
            user.stopUsingItem();
        }
        return TypedActionResult.fail(stack);
    }

    /* ---------- Vanilla item use ---------- */
    @Override public int getMaxUseTime(ItemStack stack) { return 72000; }
    @Override public UseAction getUseAction(ItemStack stack) { return UseAction.NONE; }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        // ✅ server-side gate
        if (!world.isClient && !canUseVacuum(user)) {
            return denyUse(world, user, stack);
        }

        user.setCurrentHand(hand);
        // ✅ sounds handled by client loop controller
        return TypedActionResult.consume(stack);
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        // ✅ stop sound handled by client loop controller
    }

    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        if (world.isClient || !(user instanceof PlayerEntity p)) return;

        // ✅ gate (covers forced/edge cases)
        if (!canUseVacuum(p)) {
            p.stopUsingItem();
            return;
        }

        if ((world.getTime() % DRAIN_EVERY_T) != 0) return;

        // ✅ IMPORTANT: remove TOTAL-cap check (cap is per essence now)
        // (EssenceStorage.add handles clamping per-essence)

        // Cone apex/axis from the nozzle so it aligns with the beam
        final Vec3d apex = nozzlePos(p);
        final Vec3d axis = p.getRotationVec(1f).normalize();

        /* 1) Entity inside cone (nearest along axis) */
        LivingEntity target = findEntityInCone((ServerWorld) world, p, apex, axis, RANGE);
        if (target != null) {
            EssenceType t = classifyEntity(target);
            if (t != null) {
                int put = EssenceStorage.add(stack, t, ADD_PER_TICK);
                if (put > 0) {
                    EssenceStorage.setSelectedAnim(stack, t);
                    playEssencePing(p, t);

                    // ✅ durability only when essence was gained
                    damageOnUse(p, stack);

                    if (t == EssenceType.DEATH || t == EssenceType.LIFE) {
                        DamageSource src = ((ServerWorld) world).getDamageSources().magic();
                        target.damage(src, DOT_DAMAGE);
                        if (t == EssenceType.DEATH)
                            target.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 25, 0, false, true, true));
                        else
                            target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 25, 0, false, true, true));
                    }

                    Vec3d srcPos = target.getPos().add(0, target.getStandingEyeHeight() * 0.6, 0);
                    spawnEssenceDrift((ServerWorld) world, p, srcPos, t, stack);
                    return;
                }
            }
        }

        /* 2) Blocks inside the cone: prefer plants (LIGHT) */
        BlockPos plant = findFirstPlantInCone(world, apex, axis, RANGE);
        if (plant != null) {
            int put = EssenceStorage.add(stack, EssenceType.LIGHT, ADD_PER_TICK);
            if (put > 0) {
                EssenceStorage.setSelectedAnim(stack, EssenceType.LIGHT);
                playEssencePing(p, EssenceType.LIGHT);

                // ✅ durability only when essence was gained
                damageOnUse(p, stack);

                world.breakBlock(plant, false, p);
                spawnEssenceDrift((ServerWorld) world, p, Vec3d.ofCenter(plant), EssenceType.LIGHT, stack);
                return;
            }
        }

        /* 3) GAIA: no block breaking, only deep overworld + low light */
        if (tryAbsorbGaia((ServerWorld) world, p, stack)) {
            // ✅ durability only when essence was gained (tryAbsorbGaia only returns true if added)
            damageOnUse(p, stack);

            if (p instanceof ServerPlayerEntity sp) {
                this.triggerAnim(sp, p.getActiveHand().ordinal(), "main", "gaia_hold");
            }
            stack.getOrCreateNbt().putInt("odd_gaiaHoldT", 6);
            stack.getOrCreateNbt().putBoolean("odd_gaiaActive", true);
            return;
        }

        /* 4) Air sample */
        EssenceType air = pickAirEssence((ServerWorld) world, p.getBlockPos());
        if (air != null) {
            int put = EssenceStorage.add(stack, air, ADD_PER_TICK);
            if (put > 0) {
                EssenceStorage.setSelectedAnim(stack, air);
                playEssencePing(p, air);

                // ✅ durability only when essence was gained
                damageOnUse(p, stack);

                Vec3d src = p.getEyePos().add(p.getRotationVec(1f).multiply(2.0));
                spawnEssenceDrift((ServerWorld) world, p, src, air, stack);
            }
        }
    }

    /** ✅ Consumes durability (respects Unbreaking), allows Mending to restore later. */
    private static void damageOnUse(PlayerEntity p, ItemStack stack) {
        if (!stack.isDamageable()) return;
        stack.damage(1, p, pl -> pl.sendToolBreakStatus(pl.getActiveHand()));
    }

    /* ====== GAIA rules ====== */

    private static boolean tryAbsorbGaia(ServerWorld sw, PlayerEntity p, ItemStack stack) {
        if (sw.getRegistryKey() != World.OVERWORLD) return false;

        BlockPos sample = p.getBlockPos().down();
        if (sample.getY() > GAIA_MAX_Y) return false;
        if (sw.isSkyVisible(sample)) return false;

        int combined = sw.getLightLevel(sample);
        int sky = sw.getLightLevel(LightType.SKY, sample);
        if (combined > GAIA_MAX_LIGHT) return false;
        if (sky > GAIA_MAX_SKY) return false;

        BlockState below = sw.getBlockState(sample);
        if (below.isAir()) return false;
        if (!below.isSolidBlock(sw, sample)) return false;

        int put = EssenceStorage.add(stack, EssenceType.GAIA, ADD_PER_TICK);
        if (put <= 0) return false;

        EssenceStorage.setSelectedAnim(stack, EssenceType.GAIA);
        playEssencePing(p, EssenceType.GAIA);
        spawnEssenceDrift(sw, p, Vec3d.ofCenter(sample), EssenceType.GAIA, stack);
        return true;
    }

    /* ====== Cone helpers ====== */

    private static boolean insideCone(Vec3d apex, Vec3d axisNorm, Vec3d point, double range) {
        Vec3d v = point.subtract(apex);
        double z = v.dotProduct(axisNorm);
        if (z <= 0 || z > range) return false;
        Vec3d radial = v.subtract(axisNorm.multiply(z));
        double r2 = radial.lengthSquared();
        double maxR = z * CONE_TAN;
        return r2 <= (maxR * maxR);
    }

    private static LivingEntity findEntityInCone(ServerWorld sw, PlayerEntity owner, Vec3d apex, Vec3d axis, double range) {
        Box box = new Box(apex, apex).expand(range);
        Predicate<Entity> pred = e ->
                e instanceof LivingEntity le && le.isAlive() && e != owner &&
                        insideCone(apex, axis, le.getPos().add(0, le.getStandingEyeHeight() * 0.6, 0), range);

        double best = Double.MAX_VALUE;
        LivingEntity bestLe = null;
        for (Entity e : sw.getOtherEntities(owner, box, pred)) {
            Vec3d v = e.getPos().add(0, ((LivingEntity) e).getStandingEyeHeight() * 0.6, 0).subtract(apex);
            double z = v.dotProduct(axis);
            if (z < best) { best = z; bestLe = (LivingEntity) e; }
        }
        return bestLe;
    }

    private static BlockPos findFirstPlantInCone(World w, Vec3d apex, Vec3d axis, double range) {
        for (double d = STEP; d <= range; d += STEP) {
            Vec3d p = apex.add(axis.multiply(d));
            BlockPos bp = BlockPos.ofFloored(p);
            BlockState st = w.getBlockState(bp);
            if (isPlant(st) && insideCone(apex, axis, Vec3d.ofCenter(bp), range)) return bp;
        }
        return null;
    }

    /* ====== Classification & misc ====== */

    private static EssenceType classifyEntity(LivingEntity le) {
        return (le.getGroup() == EntityGroup.UNDEAD) ? EssenceType.DEATH : EssenceType.LIFE;
    }

    private static boolean isPlant(BlockState st) {
        return st.getBlock() instanceof PlantBlock
                || st.getBlock().getTranslationKey().contains("flower")
                || st.getBlock().getTranslationKey().contains("sapling")
                || st.getBlock().getTranslationKey().contains("tall_grass")
                || st.getBlock().getTranslationKey().contains("fern");
    }

    private static EssenceType pickAirEssence(ServerWorld sw, BlockPos pos) {
        Biome b = sw.getBiome(pos).value();
        float t = b.getTemperature();
        if (t >= 1.0f) return EssenceType.HOT;
        if (t <= 0.30f) return EssenceType.COLD;
        return null;
    }

    /* ====== Essence “ping” sound (only when essence is actually gained) ====== */

    private static void playEssencePing(PlayerEntity p, EssenceType t) {
        if (!(p instanceof ServerPlayerEntity sp)) return;

        float pitch = switch (t) {
            case GAIA -> 0.55f;
            case COLD -> 0.85f;
            case HOT  -> 1.55f;
            case LIGHT-> 2.00f;
            case LIFE -> 1.20f;
            case DEATH-> 0.70f;
            default   -> 1.00f;
        };

        sp.playSound(ModSounds.VACUUM_ESSENCE, SoundCategory.PLAYERS, 0.9f, pitch);
    }

    /* ====== Minimal drift wisps toward the nozzle ====== */

    private static Vec3d nozzlePos(PlayerEntity p) {
        return p.getEyePos()
                .add(p.getRotationVec(1f).multiply(0.8))
                .add(0.0, -0.10, 0.0);
    }

    private static void spawnEssenceDrift(ServerWorld sw, PlayerEntity p, Vec3d source, EssenceType type, ItemStack stack) {
        var n = stack.getOrCreateNbt();
        float phase = n.getFloat("odd_driftPhase");
        phase = (phase + 0.12f) % 1.0f;
        n.putFloat("odd_driftPhase", phase);

        Vec3d nozzle = nozzlePos(p);

        var c0 = net.seep.odd.abilities.artificer.EssenceColors.start(type);
        var c1 = net.seep.odd.abilities.artificer.EssenceColors.end();

        for (int i = 0; i < 2; i++) {
            double f = Math.min(1.0, phase + i * 0.16);
            Vec3d at = source.lerp(nozzle, f);

            double jx = (sw.random.nextDouble() - 0.5) * 0.03;
            double jy = (sw.random.nextDouble() - 0.5) * 0.03;
            double jz = (sw.random.nextDouble() - 0.5) * 0.03;

            var effect = new net.minecraft.particle.DustColorTransitionParticleEffect(c0, c1, 1.0f);
            sw.spawnParticles(effect, at.x + jx, at.y + jy, at.z + jz, 1, 0, 0, 0, 0.0);
        }
    }

    /* ====== GeckoLib: model/renderer/controller ====== */

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Supplier<Object> renderProvider = GeoItem.makeRenderer(this);
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar ctrs) {
        AnimationController<ArtificerVacuumItem> ctrl =
                new AnimationController<>(this, "main", 8, state -> {
                    state.setAndContinue(IDLE);
                    return PlayState.CONTINUE;
                });

        ctrl.triggerableAnim("gaia_hold", RawAnimation.begin().thenPlayAndHold("select_gaia"));
        ctrl.triggerableAnim("reset_to_idle", RawAnimation.begin().thenPlay("idle"));
        ctrs.add(ctrl);
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
    @Override public Supplier<Object> getRenderProvider() { return renderProvider; }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (world.isClient) return;

        // ✅ don't run anim reset logic for non-artificers
        if (entity instanceof PlayerEntity pe && !canUseVacuum(pe)) return;

        var n = stack.getOrCreateNbt();
        int t = n.getInt("odd_gaiaHoldT");
        if (t > 0) {
            n.putInt("odd_gaiaHoldT", t - 1);
        } else if (n.getBoolean("odd_gaiaActive")) {
            n.putBoolean("odd_gaiaActive", false);

            if (entity instanceof ServerPlayerEntity sp) {
                Hand hand = null;
                if (sp.getMainHandStack() == stack) hand = Hand.MAIN_HAND;
                else if (sp.getOffHandStack() == stack) hand = Hand.OFF_HAND;
                if (hand != null) this.triggerAnim(sp, hand.ordinal(), "main", "reset_to_idle");
            }
        }
    }

    @Override
    public void createRenderer(Consumer<Object> consumer) {
        consumer.accept(new RenderProvider() {
            private GeoItemRenderer<?> renderer;
            @Override
            public GeoItemRenderer<?> getCustomRenderer() {
                if (renderer == null) {
                    renderer = new net.seep.odd.abilities.artificer.item.client.ArtificerVacuumRenderer();
                }
                return renderer;
            }
        });
    }
}