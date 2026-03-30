package net.seep.odd.block.rps_machine.game;

public enum RpsRoundResult {
    WIN,
    LOSE,
    DRAW;

    public static RpsRoundResult byId(int id) {
        if (id < 0 || id >= values().length) return null;
        return values()[id];
    }
}