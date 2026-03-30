package net.seep.odd.block.rps_machine.game;

public enum RpsPhase {
    IDLE,
    PLAYER_CHOOSE,
    VICTORY,
    DEFEAT;

    public static RpsPhase byId(int id) {
        if (id < 0 || id >= values().length) return IDLE;
        return values()[id];
    }
}