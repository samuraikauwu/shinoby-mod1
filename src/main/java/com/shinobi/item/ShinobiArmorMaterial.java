package com.shinobi.item;

import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.Items;
import net.minecraft.recipe.Ingredient;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

/** Лёгкая броня шиноби: мало защиты, зато даёт бонус к осанке и скорости. */
public class ShinobiArmorMaterial implements ArmorMaterial {
    public static final ShinobiArmorMaterial INSTANCE = new ShinobiArmorMaterial();

    private static final int[] DURABILITY = {13, 15, 16, 11};
    private static final int[] PROTECTION = {1, 3, 4, 1};

    private static int idx(ArmorItem.Type type) {
        return switch (type) {
            case HELMET -> 0;
            case CHESTPLATE -> 1;
            case LEGGINGS -> 2;
            case BOOTS -> 3;
        };
    }

    @Override
    public int getDurability(ArmorItem.Type type) {
        return DURABILITY[idx(type)] * 18;
    }

    @Override
    public int getProtection(ArmorItem.Type type) {
        return PROTECTION[idx(type)];
    }

    @Override
    public int getEnchantability() {
        return 16;
    }

    @Override
    public SoundEvent getEquipSound() {
        return SoundEvents.ITEM_ARMOR_EQUIP_LEATHER;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.ofItems(Items.LEATHER);
    }

    @Override
    public String getName() {
        return "shinobi:shinobi";
    }

    @Override
    public float getToughness() {
        return 0.0f;
    }

    @Override
    public float getKnockbackResistance() {
        return 0.0f;
    }
}
