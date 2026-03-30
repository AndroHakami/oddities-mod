package net.seep.odd.block.rps_machine.game;

public enum RpsMove {
    ROCK(0, "Rock"),
    PAPER(1, "Paper"),
    SCISSORS(2, "Scissors");

    private final int buttonId;
    private final String displayName;

    RpsMove(int buttonId, String displayName) {
        this.buttonId = buttonId;
        this.displayName = displayName;
    }

    public int getButtonId() {
        return buttonId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int versus(RpsMove other) {
        if (other == null || this == other) return 0;

        return switch (this) {
            case ROCK -> (other == SCISSORS) ? 1 : -1;
            case PAPER -> (other == ROCK) ? 1 : -1;
            case SCISSORS -> (other == PAPER) ? 1 : -1;
        };
    }

    public static RpsMove fromButtonId(int id) {
        for (RpsMove move : values()) {
            if (move.buttonId == id) return move;
        }
        return null;
    }

    public static RpsMove byId(int id) {
        if (id < 0 || id >= values().length) return null;
        return values()[id];
    }
}