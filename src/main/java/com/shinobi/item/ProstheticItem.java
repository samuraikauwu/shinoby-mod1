package com.shinobi.item;

import com.shinobi.ShinobiUtil;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/** Инструмент протеза шиноби. Расходует духовные эмблемы, уровень улучшается у Идола Скульптора. */
public class ProstheticItem extends Item {
    public static final int MAX_LEVEL = 3;
    public final ProstheticTools.Tool tool;

    public ProstheticItem(ProstheticTools.Tool tool, Settings settings) {
        super(settings);
        this.tool = tool;
    }

    public static int level(ItemStack stack) {
        return stack.hasNbt() ? stack.getNbt().getInt("ToolLevel") : 0;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient || !(user instanceof ServerPlayerEntity sp)) {
            return TypedActionResult.success(stack, world.isClient());
        }
        if (!ShinobiUtil.hasEmblems(sp, tool.cost)) {
            sp.sendMessage(Text.translatable("msg.shinobi.no_emblems"), true);
            return TypedActionResult.fail(stack);
        }
        if (ProstheticTools.activate(tool, (ServerWorld) world, sp, level(stack))) {
            ShinobiUtil.takeEmblems(sp, tool.cost);
            sp.getItemCooldownManager().set(this, tool.cooldown);
            return TypedActionResult.success(stack, true);
        }
        return TypedActionResult.fail(stack);
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("tooltip.shinobi.level", level(stack), MAX_LEVEL).formatted(Formatting.GOLD));
        tooltip.add(Text.translatable("tooltip.shinobi.cost", tool.cost).formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.shinobi.upgrade_hint").formatted(Formatting.DARK_GRAY));
    }
}
