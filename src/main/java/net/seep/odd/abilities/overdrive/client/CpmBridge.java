package net.seep.odd.abilities.overdrive.client;

// Minimal wrapper so the rest of your code doesn’t directly depend on CPM classes.
// Replace the TODOs with the actual CPM API calls you prefer.

public final class CpmBridge {
    private CpmBridge() {}

    public static void playGestureLoop(String name) {
        try {
            // TODO: call CPM API to start a looping gesture named `name`
            // Example (pseudo):
            // CustomPlayerModelsAPI.get().playGesture(name, true);
        } catch (Throwable ignored) {}
    }

    public static void stopGesture(String name) {
        try {
            // TODO: call CPM API to stop the gesture named `name`
            // CustomPlayerModelsAPI.get().stopGesture(name);
        } catch (Throwable ignored) {}
    }
}
