package net.seep.odd.item.custom.client;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.seep.odd.Oddities;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public abstract class AbstractBounceHatModel<T extends ArmorItem & GeoItem> extends GeoModel<T> {
    private final String assetName;
    private final Map<UUID, SpringState> springs = new HashMap<>();

    protected AbstractBounceHatModel(String assetName) {
        this.assetName = assetName;
    }

    @Override
    public Identifier getModelResource(T animatable) {
        return new Identifier(Oddities.MOD_ID, "geo/" + assetName + ".geo.json");
    }

    @Override
    public Identifier getTextureResource(T animatable) {
        return new Identifier(Oddities.MOD_ID, "textures/armor/" + assetName + ".png");
    }

    @Override
    public Identifier getAnimationResource(T animatable) {
        return new Identifier(Oddities.MOD_ID, "animations/" + assetName + ".animation.json");
    }

    @Override
    public void setCustomAnimations(T animatable, long instanceId, AnimationState<T> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);

        GeoBone hatBone = (GeoBone) this.getAnimationProcessor().getBone("bone");
        if (hatBone == null) {
            return;
        }

        Entity entity = animationState.getData(DataTickets.ENTITY);
        if (!(entity instanceof LivingEntity living)) {
            hatBone.setPosY(0.0f);
            hatBone.setRotX(0.0f);
            hatBone.setRotY(0.0f);
            hatBone.setRotZ(0.0f);
            return;
        }

        SpringState state = springs.computeIfAbsent(living.getUuid(), uuid -> new SpringState());

        boolean airborne = !living.isOnGround();
        float verticalSpeed = (float) living.getVelocity().y;
        float horizontalSpeed = (float) Math.sqrt(
                living.getVelocity().x * living.getVelocity().x +
                        living.getVelocity().z * living.getVelocity().z
        );

        float age = living.age + animationState.getPartialTick();

        float targetLift = 0.0f;
        float targetPitch = 0.0f;
        float targetRoll = 0.0f;

        if (airborne) {
            // Small lift while airborne, slightly more when falling.
            float fallAmount = MathHelper.clamp(-verticalSpeed, 0.0f, 0.9f);
            targetLift = 0.08f + fallAmount * 0.14f; // max around 0.20

            // Tiny forward/back tilt only.
            targetPitch = MathHelper.clamp(-verticalSpeed * 0.10f, -0.04f, 0.08f);

            // Tiny roll sway, mostly from lateral motion.
            float sway = Math.min(0.06f, horizontalSpeed * 0.12f + 0.01f);
            targetRoll = MathHelper.sin(age * 0.18f) * sway;
        }

        // Landing bounce: tiny settle downward, then reconnect.
        if (state.wasAirborne && !airborne) {
            state.liftVelocity -= 0.065f;
            state.pitchVelocity *= -0.20f;
            state.rollVelocity *= -0.30f;
        }

        applySpring(state, targetLift, targetPitch, targetRoll);

        hatBone.setPosY(state.lift);
        hatBone.setRotX(state.pitch);
        hatBone.setRotY(0.0f); // avoid fighting head rotation
        hatBone.setRotZ(state.roll);

        state.wasAirborne = airborne;
    }

    private static void applySpring(SpringState state, float targetLift, float targetPitch, float targetRoll) {
        state.liftVelocity = (state.liftVelocity + (targetLift - state.lift) * 0.18f) * 0.68f;
        state.pitchVelocity = (state.pitchVelocity + (targetPitch - state.pitch) * 0.16f) * 0.66f;
        state.rollVelocity = (state.rollVelocity + (targetRoll - state.roll) * 0.16f) * 0.66f;

        state.lift += state.liftVelocity;
        state.pitch += state.pitchVelocity;
        state.roll += state.rollVelocity;
    }

    private static final class SpringState {
        private float lift;
        private float pitch;
        private float roll;

        private float liftVelocity;
        private float pitchVelocity;
        private float rollVelocity;

        private boolean wasAirborne;
    }
}