package net.seep.odd.item.custom;

import java.util.function.Consumer;

public class BlueTrumpetAxeItem extends TrumpetAxeItem {
    public BlueTrumpetAxeItem(Settings settings) {
        super(settings);
    }

    @Override
    public void createRenderer(Consumer<Object> consumer) {
        GeoItemClientHooks.createBuiltinItemRenderer(
                consumer,
                "net.seep.odd.item.custom.client.BlueTrumpetAxeRenderer"
        );
    }
}
