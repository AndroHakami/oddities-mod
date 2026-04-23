package net.seep.odd.item.custom;

import java.util.function.Consumer;

public class PinkTrumpetAxeItem extends TrumpetAxeItem {
    public PinkTrumpetAxeItem(Settings settings) {
        super(settings);
    }

    @Override
    public void createRenderer(Consumer<Object> consumer) {
        GeoItemClientHooks.createBuiltinItemRenderer(
                consumer,
                "net.seep.odd.item.custom.client.PinkTrumpetAxeRenderer"
        );
    }
}
