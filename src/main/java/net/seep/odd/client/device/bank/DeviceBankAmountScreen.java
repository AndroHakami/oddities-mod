package net.seep.odd.client.device.bank;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.device.bank.DabloonBankManager;
import net.seep.odd.device.bank.DabloonBankNetworking;

@Environment(EnvType.CLIENT)
public final class DeviceBankAmountScreen extends Screen {
    private static final Identifier DABLOON_ID = new Identifier(Oddities.MOD_ID, "dabloon");

    private final Screen parent;
    private final boolean deposit;

    private TextFieldWidget amountField;
    private String status = "";

    public DeviceBankAmountScreen(Screen parent, boolean deposit) {
        super(Text.literal(deposit ? "Deposit Dabloons" : "Withdraw Dabloons"));
        this.parent = parent;
        this.deposit = deposit;
    }

    @Override
    protected void init() {
        int left = this.width / 2 - 120;
        int top = this.height / 2 - 70;

        this.amountField = new TextFieldWidget(this.textRenderer, left + 20, top + 42, 200, 20, Text.literal("Amount"));
        this.amountField.setMaxLength(4);
        this.amountField.setPlaceholder(Text.literal("1 - 999"));
        this.amountField.setText("1");

        this.addSelectableChild(this.amountField);
        this.setInitialFocus(this.amountField);

        this.addDrawableChild(ButtonWidget.builder(Text.literal(deposit ? "Deposit" : "Withdraw"), b -> confirm())
                .dimensions(left + 20, top + 90, 94, 20)
                .build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> close())
                .dimensions(left + 126, top + 90, 94, 20)
                .build());
    }

    private void confirm() {
        int amount;
        try {
            amount = Integer.parseInt(amountField.getText().trim());
        } catch (Exception e) {
            status = "Enter a number.";
            return;
        }

        amount = Math.max(1, Math.min(DabloonBankManager.MAX_PER_ACTION, amount));

        var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeVarInt(amount);

        ClientPlayNetworking.send(deposit ? DabloonBankNetworking.C2S_DEPOSIT : DabloonBankNetworking.C2S_WITHDRAW, buf);

        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }

    private int countOnHand() {
        if (this.client == null || this.client.player == null) return 0;

        Item dabloon = Registries.ITEM.get(DABLOON_ID);
        int count = 0;

        for (int i = 0; i < this.client.player.getInventory().size(); i++) {
            ItemStack stack = this.client.player.getInventory().getStack(i);
            if (stack.isOf(dabloon)) {
                count += stack.getCount();
            }
        }

        return count;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (Character.isDigit(chr)) {
            return amountField != null && amountField.charTyped(chr, modifiers);
        }
        return chr == '\b' || super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (amountField != null && amountField.keyPressed(keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void tick() {
        if (amountField != null) amountField.tick();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);

        int left = this.width / 2 - 120;
        int top = this.height / 2 - 70;

        context.fill(left, top, left + 240, top + 124, 0xE0101420);
        context.fill(left + 1, top + 1, left + 239, top + 123, 0xD1161C29);

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, top + 10, 0xFFFFFFFF);

        int shown = deposit ? countOnHand() : DabloonBankClientCache.balance();
        String helper = deposit ? "On hand: " + shown : "In bank: " + shown;
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(helper), this.width / 2, top + 28, 0xFFBFD0EC);

        amountField.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Max per action: 999"), this.width / 2, top + 68, 0xFF93A7C8);

        if (!status.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(status), this.width / 2, top + 116, 0xFFFFD0A0);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}