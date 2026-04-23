package net.seep.odd.entity.dragoness;

public enum DragonessAttackType {
    NONE("idle", 0, false, false),

    LASER("attack_laser", DragonessEntity.LASER_TOTAL_TICKS, false, false),

    FLIGHT_STANCE("stance_flight", DragonessEntity.FLIGHT_STANCE_TOTAL_TICKS, false, true),
    FLIGHT_LOOP("flight", 0, true, true),
    FLIGHT_THROW("flight_to_throw", DragonessEntity.FLIGHT_THROW_TOTAL_TICKS, false, true),

    KICKS("attack_kicks", DragonessEntity.KICK_TOTAL_TICKS, false, false),

    BREAKER("attack_break", DragonessEntity.BREAKER_TOTAL_TICKS, false, true),
    BREAKER_BACKFLIP("backflip", DragonessEntity.BREAKER_BACKFLIP_TOTAL_TICKS, false, false),

    SLIDE_DASH_STANCE("stance_kick_dash", DragonessEntity.SLIDE_DASH_STANCE_TOTAL_TICKS, false, false),
    SLIDE_DASH("kick_dash", DragonessEntity.SLIDE_DASH_TOTAL_TICKS, true, false),

    CRASH_DOWN("attack_crash", DragonessEntity.CRASH_TOTAL_TICKS, false, true),

    CHILL_STANCE("stance_sit", DragonessEntity.CHILL_STANCE_TOTAL_TICKS, false, true),
    CHILL_LOOP("sitting", 0, true, true),
    CHILL_DISTURBED("sitting_disturped", DragonessEntity.CHILL_DISTURBED_TOTAL_TICKS, false, true),

    COMBO_DASH_STANCE("stance_combo_dash", DragonessEntity.COMBO_DASH_STANCE_TOTAL_TICKS, false, false),
    COMBO_HIT1("combo_hit1", DragonessEntity.COMBO_HIT1_TOTAL_TICKS, false, true),
    COMBO_HIT2("combo_hit2", DragonessEntity.COMBO_HIT2_TOTAL_TICKS, false, true),
    COMBO_FLY_DOWN("combo_fly_down", 0, true, true);

    private final String animation;
    private final int totalTicks;
    private final boolean looping;
    private final boolean airborne;

    DragonessAttackType(String animation, int totalTicks, boolean looping, boolean airborne) {
        this.animation = animation;
        this.totalTicks = totalTicks;
        this.looping = looping;
        this.airborne = airborne;
    }

    public String animation() {
        return this.animation;
    }

    public int totalTicks() {
        return this.totalTicks;
    }

    public boolean looping() {
        return this.looping;
    }

    public boolean airborne() {
        return this.airborne;
    }
}
