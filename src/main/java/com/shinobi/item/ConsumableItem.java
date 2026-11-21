package com.shinobi.item;

import com.shinobi.data.PlayerData;
import com.shinobi.data.ShinobiState;
import com.shinobi.net.ShinobiNet;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** Предмет с действием, меняющим данные игрока (тыква, семена, чётки, память, свитки). */
public class ConsumableItem extends Item {
    public interface Use {
        boolean apply(ServerPlayerEntity p, PlayerData d, ItemStack stack);
    }

    private final Use use;
    private final boolean consume;
    private final int cooldown;

    public ConsumableItem(Settings settings, boolean consume, int cooldown, Use use) {
        super(settings);
        this.use = use;
        this.consume = consume;
        this.cooldown = cooldown;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient || !(user instanceof ServerPlayerEntity sp)) {
            return TypedActionResult.success(stack, world.isClient());
        }
        PlayerData d = ShinobiState.of(sp);
        if (use.apply(sp, d, stack)) {
            if (consume && !sp.isCreative()) stack.decrement(1);
            if (cooldown > 0) sp.getItemCooldownManager().set(this, cooldown);
            ShinobiState.save(sp);
            ShinobiNet.sync(sp);
            return TypedActionResult.success(stack, true);
        }
        return TypedActionResult.fail(stack);
    }
}
