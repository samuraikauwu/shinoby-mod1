package com.shinobi;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

public final class ShinobiUtil {
    private ShinobiUtil() {
    }

    public static boolean hasEmblems(ServerPlayerEntity p, int n) {
        return p.isCreative() || n <= 0 || p.getInventory().count(ModItems.SPIRIT_EMBLEM) >= n;
    }

    public static void takeEmblems(ServerPlayerEntity p, int n) {
        if (p.isCreative() || n <= 0) return;
        if (com.shinobi.combat.Skill.has(p, com.shinobi.combat.Skill.EMBLEM_SAVER) && p.getRandom().nextFloat() < 0.25f) return;
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.size() && n > 0; i++) {
            ItemStack s = inv.getStack(i);
            if (s.isOf(ModItems.SPIRIT_EMBLEM)) {
                int t = Math.min(n, s.getCount());
                s.decrement(t);
                n -= t;
            }
        }
    }

    /** Горизонтальное направление взгляда (нормализованное). */
    public static Vec3d horizontalLook(Entity e) {
        Vec3d l = e.getRotationVec(1f);
        Vec3d h = new Vec3d(l.x, 0, l.z);
        return h.lengthSquared() < 1.0E-6 ? new Vec3d(0, 0, 1) : h.normalize();
    }

    /** Задать скорость игроку и синхронизировать с клиентом. */
    public static void velocity(ServerPlayerEntity p, double x, double y, double z) {
        p.setVelocity(x, y, z);
        p.velocityModified = true;
        p.fallDistance = 0;
        p.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(p));
    }
}
