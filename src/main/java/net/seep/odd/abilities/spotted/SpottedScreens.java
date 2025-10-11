package net.seep.odd.abilities.spotted;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;
import net.minecraft.resource.featuretoggle.FeatureFlags; // <-- 1.20.1
import net.seep.odd.Oddities;

public final class SpottedScreens {
    private SpottedScreens() {}

    public static ScreenHandlerType<PhantomBuddyScreenHandler> PHANTOM_BUDDY;

    /** Call from common init. */
    public static void register() {
        if (PHANTOM_BUDDY == null) {
            PHANTOM_BUDDY = Registry.register(
                    Registries.SCREEN_HANDLER,
                    new Identifier(Oddities.MOD_ID, "phantom_buddy"),
                    // 1.20.1 requires the feature flags param:
                    new ScreenHandlerType<>(PhantomBuddyScreenHandler::new, FeatureFlags.VANILLA_FEATURES)
            );
        }
    }
}
