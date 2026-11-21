package com.shinobi;

import com.shinobi.block.SculptorIdolBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;

public final class ModBlocks {
    public static final Block SCULPTORS_IDOL = new SculptorIdolBlock(
            AbstractBlock.Settings.create().strength(3.0f, 6.0f).requiresTool().sounds(BlockSoundGroup.STONE).luminance(s -> 7));

    public static BlockItem SCULPTORS_IDOL_ITEM;

    private ModBlocks() {
    }

    public static void register() {
        Registry.register(Registries.BLOCK, ShinobiMod.id("sculptors_idol"), SCULPTORS_IDOL);
        SCULPTORS_IDOL_ITEM = Registry.register(Registries.ITEM, ShinobiMod.id("sculptors_idol"),
                new BlockItem(SCULPTORS_IDOL, new Item.Settings()));
    }
}
