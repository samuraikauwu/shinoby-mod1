package com.shinobi.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsage;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.recipe.Ingredient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

import java.util.List;

/** Катана: удерживайте ПКМ для защиты; нажатие в самый момент удара — идеальное отражение. */
public class KatanaItem extends SwordItem {
    public final float postureMult;

    public KatanaItem(ToolMaterial material, int damage, float speed, float postureMult, Settings settings) {
        super(material, damage, speed, settings);
        this.postureMult = postureMult;
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.BLOCK;
    }

    @Override
    public int getMaxUseTime(ItemStack stack) {
        return 72000;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        return ItemUsage.consumeHeldItem(world, user, hand);
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("tooltip.shinobi.posture", String.format("x%.1f", postureMult)).formatted(Formatting.GOLD));
        tooltip.add(Text.translatable("tooltip.shinobi.katana_hint").formatted(Formatting.GRAY));
    }

    /** Материал катаны. */
    public static class Material implements ToolMaterial {
        private final int durability;
        private final Ingredient repair;

        public Material(int durability, Ingredient repair) {
            this.durability = durability;
            this.repair = repair;
        }

        @Override
        public int getDurability() {
            return durability;
        }

        @Override
        public float getMiningSpeedMultiplier() {
            return 6.0f;
        }

        @Override
        public float getAttackDamage() {
            return 0.0f;
        }

        @Override
        public int getMiningLevel() {
            return 2;
        }

        @Override
        public int getEnchantability() {
            return 15;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return repair;
        }
    }
}
