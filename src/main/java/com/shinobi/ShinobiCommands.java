package com.shinobi;

import com.mojang.brigadier.CommandDispatcher;
import com.shinobi.combat.Art;
import com.shinobi.combat.PostureSystem;
import com.shinobi.data.PlayerData;
import com.shinobi.data.ShinobiState;
import com.shinobi.item.ProstheticItem;
import com.shinobi.net.ShinobiNet;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * /shinobi kit        — стартовый набор
 * /shinobi unlockall  — изучить все приёмы (оп)
 * /shinobi reset      — сбросить прогресс (оп)
 */
public final class ShinobiCommands {
    private ShinobiCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> build(dispatcher));
    }

    private static void build(CommandDispatcher<ServerCommandSource> d) {
        d.register(CommandManager.literal("shinobi")
                .then(CommandManager.literal("kit").executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                    give(p, new ItemStack(ModItems.KUSABIMARU));
                    give(p, new ItemStack(ModItems.HEALING_GOURD));
                    give(p, new ItemStack(ModItems.SPIRIT_EMBLEM, 24));
                    give(p, new ItemStack(ModItems.SCROLLS.get(Art.ICHIMONJI)));
                    give(p, new ItemStack(ModItems.TOOLS.get(com.shinobi.item.ProstheticTools.Tool.GRAPPLE)));
                    give(p, new ItemStack(ModItems.TOOLS.get(com.shinobi.item.ProstheticTools.Tool.SHURIKEN)));
                    give(p, new ItemStack(ModItems.TOOLS.get(com.shinobi.item.ProstheticTools.Tool.UMBRELLA)));
                    ctx.getSource().sendFeedback(() -> Text.translatable("msg.shinobi.kit_given"), false);
                    return 1;
                }))
                .then(CommandManager.literal("unlockall").requires(s -> s.hasPermissionLevel(2)).executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                    PlayerData data = ShinobiState.of(p);
                    for (Art a : Art.values()) data.unlock(a.ordinal());
                    ShinobiState.save(p);
                    ShinobiNet.sync(p);
                    ctx.getSource().sendFeedback(() -> Text.translatable("msg.shinobi.unlocked_all"), false);
                    return 1;
                }))
                .then(CommandManager.literal("adventure").requires(s -> s.hasPermissionLevel(2)).executes(ctx -> {
                    com.shinobi.world.AdventureMap.build(ctx.getSource().getPlayerOrThrow());
                    return 1;
                }))
                .then(CommandManager.literal("reset").requires(s -> s.hasPermissionLevel(2)).executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                    PlayerData data = ShinobiState.of(p);
                    data.attackPower = 0;
                    data.necklaces = 0;
                    data.gourdSeeds = 0;
                    data.gourdUses = data.maxGourd();
                    data.resurrections = data.maxResurrections();
                    data.skillPoints = 0;
                    data.unlocked = 1;
                    data.selectedArt = 0;
                    data.killCounter = 0;
                    ShinobiMod.applyAttributes(p, data);
                    PostureSystem.reset(p);
                    ShinobiState.save(p);
                    ShinobiNet.sync(p);
                    ctx.getSource().sendFeedback(() -> Text.translatable("msg.shinobi.reset_done"), false);
                    return 1;
                })));
    }

    private static void give(ServerPlayerEntity p, ItemStack s) {
        if (!p.getInventory().insertStack(s)) p.dropItem(s, false);
    }

    @SuppressWarnings("unused")
    private static boolean unused(Item i) {
        return i instanceof ProstheticItem;
    }
}
