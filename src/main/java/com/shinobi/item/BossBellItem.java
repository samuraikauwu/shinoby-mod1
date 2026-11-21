package com.shinobi.item;

import com.shinobi.combat.BossManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Колокол вызова босса: ПКМ по блоку. */
public class BossBellItem extends Item {
    private final BossManager.Type type;

    public BossBellItem(BossManager.Type type, Settings settings) {
        super(settings);
        this.type = type;
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext ctx) {
        if (ctx.getWorld().isClient) return ActionResult.SUCCESS;
        if (!(ctx.getWorld() instanceof ServerWorld w) || !(ctx.getPlayer() instanceof ServerPlayerEntity p)) {
            return ActionResult.PASS;
        }
        BlockPos pos = ctx.getBlockPos().offset(ctx.getSide());
        if (BossManager.spawn(w, Vec3d.ofBottomCenter(pos), type, p)) {
            w.playSound(null, pos, SoundEvents.BLOCK_BELL_RESONATE, SoundCategory.HOSTILE, 3.0f, 0.5f);
            if (!p.isCreative()) ctx.getStack().decrement(1);
            return ActionResult.CONSUME;
        }
        return ActionResult.FAIL;
    }
}
