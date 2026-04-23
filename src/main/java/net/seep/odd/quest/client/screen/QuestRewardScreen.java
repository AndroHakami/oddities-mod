
package net.seep.odd.quest.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.seep.odd.quest.QuestDefinition;
import net.seep.odd.quest.QuestNetworking;
import net.seep.odd.quest.QuestRewardData;

public final class QuestRewardScreen extends Screen {
    private final Screen parent;
    private final QuestDefinition definition;
    private final int librarianEntityId;

    public QuestRewardScreen(Screen parent, QuestDefinition definition, int librarianEntityId) {
        super(Text.literal("Quest Rewards"));
        this.parent = parent;
        this.definition = definition;
        this.librarianEntityId = librarianEntityId;
    }

    @Override
    protected void init() {
        int panelX = this.width / 2 - 110;
        int panelY = this.height / 2 - 80;

        this.addDrawableChild(net.minecraft.client.gui.widget.ButtonWidget.builder(Text.literal("Claim Rewards"), button -> {
            net.minecraft.network.PacketByteBuf buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
            buf.writeInt(librarianEntityId);
            buf.writeString(definition.id);
            ClientPlayNetworking.send(QuestNetworking.C2S_CLAIM, buf);
            this.client.setScreen(parent);
        }).dimensions(panelX + 20, panelY + 120, 84, 20).build());

        this.addDrawableChild(net.minecraft.client.gui.widget.ButtonWidget.builder(Text.literal("Back"), button ->
                this.client.setScreen(parent)).dimensions(panelX + 116, panelY + 120, 84, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);

        int panelX = this.width / 2 - 110;
        int panelY = this.height / 2 - 80;
        context.fill(panelX, panelY, panelX + 220, panelY + 150, 0xCC111111);
        context.drawBorder(panelX, panelY, 220, 150, 0xAAFFFFFF);

        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(definition.title), this.width / 2, panelY + 10, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Rewards"), this.width / 2, panelY + 24, 0xC7C7C7);

        int y = panelY + 44;
        for (QuestRewardData reward : definition.rewards) {
            Identifier itemId = new Identifier(reward.item);
            ItemStack stack = new ItemStack(Registries.ITEM.get(itemId), Math.max(1, reward.count));
            context.fill(panelX + 18, y - 2, panelX + 202, y + 18, 0x55000000);
            context.drawItem(stack, panelX + 24, y);
            context.drawText(this.textRenderer, stack.getName(), panelX + 48, y + 4, 0xFFFFFF, false);
            context.drawText(this.textRenderer, Text.literal("x" + stack.getCount()), panelX + 170, y + 4, 0xB8FFB8, false);
            y += 24;
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
