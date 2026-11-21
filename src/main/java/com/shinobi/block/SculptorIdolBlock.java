package com.shinobi.block;

import com.shinobi.combat.PostureSystem;
import com.shinobi.data.PlayerData;
import com.shinobi.data.ShinobiState;
import com.shinobi.item.ProstheticItem;
import com.shinobi.net.ShinobiNet;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Идол Скульптора. ПКМ — отдых: полное восстановление здоровья, тыквы, воскрешений и точка возрождения.
 * Инструмент протеза в основной руке + золотые слитки во второй руке — улучшение инструмента.
 */
public class SculptorIdolBlock extends Block {
    public SculptorIdolBlock(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;
        if (!(player instanceof ServerPlayerEntity sp) || !(world instanceof ServerWorld sw)) return ActionResult.PASS;

        ItemStack main = sp.getMainHandStack();
        ItemStack off = sp.getOffHandStack();

        // Улучшение инструмента протеза
        if (main.getItem() instanceof ProstheticItem && off.isOf(Items.GOLD_INGOT)) {
            int level = ProstheticItem.level(main);
            if (level >= ProstheticItem.MAX_LEVEL) {
                sp.sendMessage(Text.translatable("msg.shinobi.max_level"), true);
                return ActionResult.CONSUME;
            }
            int need = level + 1;
            if (!sp.isCreative() && off.getCount() < need) {
                sp.sendMessage(Text.translatable("msg.shinobi.upgrade_need", need), true);
                return ActionResult.CONSUME;
            }
            if (!sp.isCreative()) off.decrement(need);
            main.getOrCreateNbt().putInt("ToolLevel", level + 1);
            sw.playSound(null, pos, SoundEvents.BLOCK_SMITHING_TABLE_USE, SoundCategory.BLOCKS, 1.0f, 1.0f);
            sp.sendMessage(Text.translatable("msg.shinobi.upgraded", level + 1), true);
            return ActionResult.CONSUME;
        }

        // Отдых
        PlayerData d = ShinobiState.of(sp);
        d.gourdUses = d.maxGourd();
        d.resurrections = d.maxResurrections();
        sp.setHealth(sp.getMaxHealth());
        sp.getHungerManager().setFoodLevel(20);
        sp.clearStatusEffects();
        PostureSystem.reset(sp);
        sp.setSpawnPoint(sw.getRegistryKey(), pos.up(), 0f, true, false);
        sw.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 25, 0.4, 0.4, 0.4, 0.02);
        sw.playSound(null, pos, SoundEvents.BLOCK_BELL_USE, SoundCategory.BLOCKS, 1.0f, 0.7f);
        sp.sendMessage(Text.translatable("msg.shinobi.rested"), true);
        ShinobiState.save(sp);
        ShinobiNet.sync(sp);
        return ActionResult.CONSUME;
    }
}
