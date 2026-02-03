package net.seep.odd.abilities.druid;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Locale;

public enum DruidForm {
    DOLPHIN(
            "dolphin",
            new Identifier("minecraft", "dolphin"),
            new Identifier("odd", "textures/gui/druid/forms/dolphin.png"),
            Text.literal("Dolphin Form")
    ),
    FOX(
            "fox",
            new Identifier("minecraft", "fox"),
            new Identifier("odd", "textures/gui/druid/forms/fox.png"),
            Text.literal("Fox Form")
    ),
    SPIDER(
            "spider",
            new Identifier("minecraft", "spider"),
            new Identifier("odd", "textures/gui/druid/forms/spider.png"),
            Text.literal("Spider Form")
    ),
    POLAR_BEAR(
            "polar_bear",
            new Identifier("minecraft", "polar_bear"),
            new Identifier("odd", "textures/gui/druid/forms/polar_bear.png"),
            Text.literal("Polar Bear Form")
    ),
    HUMAN(
            "human",
            null,
            new Identifier("odd", "textures/gui/druid/forms/human.png"),
            Text.literal("Human Form")
    );

    private final String key;
    private final Identifier identityEntityId; // used in "identity equip"
    private final Identifier iconTexture;
    private final Text displayName;

    DruidForm(String key, Identifier identityEntityId, Identifier iconTexture, Text displayName) {
        this.key = key;
        this.identityEntityId = identityEntityId;
        this.iconTexture = iconTexture;
        this.displayName = displayName;
    }

    public String key() { return key; }
    public Identifier identityEntityId() { return identityEntityId; }
    public Identifier iconTexture() { return iconTexture; }
    public Text displayName() { return displayName; }

    public static DruidForm byKey(String key) {
        if (key == null) return HUMAN;
        String k = key.toLowerCase(Locale.ROOT).trim();
        for (DruidForm f : values()) {
            if (f.key.equals(k)) return f;
        }
        return HUMAN;
    }

    public void applyBuffs(ServerPlayerEntity p) {
        clearBuffs(p);

        final boolean ambient = false;
        final boolean showParticles = false;
        final boolean showIcon = false;

        switch (this) {
            case FOX -> {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 20 * 60 * 60, 2, ambient, showParticles, showIcon));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 20 * 60 * 60, 0, ambient, showParticles, showIcon));
            }
            case SPIDER -> p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 20 * 60 * 60, 0, ambient, showParticles, showIcon));
            case POLAR_BEAR -> {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 20 * 60 * 60, 1, ambient, showParticles, showIcon));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 20 * 60 * 60, 1, ambient, showParticles, showIcon));
            }
            default -> { }
        }
    }

    public static void clearBuffs(ServerPlayerEntity p) {
        p.removeStatusEffect(StatusEffects.SPEED);
        p.removeStatusEffect(StatusEffects.STRENGTH);
        p.removeStatusEffect(StatusEffects.SLOWNESS);
        p.removeStatusEffect(StatusEffects.NIGHT_VISION);
    }
}
