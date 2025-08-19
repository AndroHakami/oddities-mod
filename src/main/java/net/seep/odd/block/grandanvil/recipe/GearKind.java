package net.seep.odd.block.grandanvil.recipe;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.*;
public enum GearKind {
    HELMET, CHESTPLATE, LEGGINGS, BOOTS,
    SWORD, AXE, PICKAXE, SHOVEL,
    SHIELD;

    public boolean matches(ItemStack stack) {
        Item it = stack.getItem();
        if (it instanceof ArmorItem a) {
            return switch (this) {
                case HELMET     -> a.getSlotType() == EquipmentSlot.HEAD;
                case CHESTPLATE -> a.getSlotType() == EquipmentSlot.CHEST;
                case LEGGINGS   -> a.getSlotType() == EquipmentSlot.LEGS;
                case BOOTS      -> a.getSlotType() == EquipmentSlot.FEET;
                default -> false;
            };
        }
        return switch (this) {
            case SWORD   -> it instanceof SwordItem;
            case AXE     -> it instanceof AxeItem;
            case PICKAXE -> it instanceof PickaxeItem;
            case SHOVEL  -> it instanceof ShovelItem;
            case SHIELD  -> it == Items.SHIELD;
            default -> false;
        };
    }
}
