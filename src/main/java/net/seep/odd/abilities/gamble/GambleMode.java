package net.seep.odd.abilities.gamble;

public enum GambleMode {
    DEBUFF, BUFF, SHOOT;

    public GambleMode next() {
        return switch (this) {
            case DEBUFF -> BUFF;
            case BUFF -> SHOOT;
            case SHOOT -> DEBUFF;
        };
    }

    public String display() {
        return switch (this) {
            case DEBUFF -> "Debuff";
            case BUFF   -> "Buff";
            case SHOOT  -> "Shoot";
        };
    }
}
