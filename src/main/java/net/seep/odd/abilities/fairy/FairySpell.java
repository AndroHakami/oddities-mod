package net.seep.odd.abilities.fairy;

public enum FairySpell {
    AURA_LEVITATION,         // UP UP UP
    AURA_HEAVY,              // UP UP DOWN
    AREA_MINE,               // UP DOWN DOWN (consumes flower)
    STONE_PRISON,            // DOWN DOWN DOWN (consumes flower)
    BUBBLE,                  // UP UP RIGHT (encase entity/players in bubble)
    AURA_REGEN,              // UP RIGHT RIGHT
    CROP_GROWTH,             // RIGHT RIGHT RIGHT
    BLACKHOLE,               // DOWN LEFT RIGHT
    RECHARGE;                // UP DOWN UP

    // 0=N,1=S,2=W,3=E
    public static FairySpell fromInputs(byte a, byte b, byte c) {
        if (a==0 && b==0 && c==0) return AURA_LEVITATION;
        if (a==0 && b==0 && c==1) return AURA_HEAVY;
        if (a==0 && b==1 && c==1) return AREA_MINE;
        if (a==1 && b==1 && c==1) return STONE_PRISON;
        if (a==0 && b==0 && c==3) return BUBBLE;
        if (a==0 && b==3 && c==3) return AURA_REGEN;
        if (a==3 && b==3 && c==3) return CROP_GROWTH;
        if (a==1 && b==2 && c==3) return BLACKHOLE;
        if (a==0 && b==1 && c==0) return RECHARGE;
        return AURA_LEVITATION;
    }

    public boolean isAura() {
        return switch (this) {
            case AURA_LEVITATION, AURA_HEAVY, AURA_REGEN, CROP_GROWTH, BLACKHOLE -> true;
            default -> false;
        };
    }

    public String textureKey() {
        return switch (this) {
            case AURA_LEVITATION -> "levitate";
            case AURA_HEAVY -> "heavy";
            case AREA_MINE -> "mine";
            case STONE_PRISON -> "stone";
            case BUBBLE -> "bubble";
            case AURA_REGEN -> "regen";
            case CROP_GROWTH -> "growth";
            case BLACKHOLE -> "blackhole";
            case RECHARGE -> "recharge";
        };
    }
}
